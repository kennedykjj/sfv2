# Daemon: robustez do socket (follow-up do #15) — spec

**Issue:** #101 · **Branch:** `feat/101-daemon-robustez-do-socket-follow-up-do-1` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o socket do daemon tem quatro fragilidades achadas na auditoria do #15: corrida na partida com socket órfão, erro de cliente engolido, laço de `accept` que gira em CPU cheia e saída do `main` sem código nem mensagem claros.
**Público:** o desenvolvedor sozinho com muitos projetos (e o que está numa empresa): precisa de um daemon único, sempre ligado, que falha de forma legível.

**Critérios de aceitação**
- AC-1: Dado dois daemons iniciados ao mesmo tempo com um socket órfão, quando ambos tentam assumir o socket, então só um assume e o outro sai com erro (exclusão por lock de arquivo no diretório `0700`, não só tentativa de conexão).
- AC-2: Dado um erro ao atender um cliente, quando ele ocorre, então é registrado em log estruturado `key=value` com o tipo do erro e sem o conteúdo da mensagem, e o servidor segue atendendo outros clientes.
- AC-3: Dado `accept` falhando repetidamente (ex.: limite de descritores), quando o laço tenta de novo, então espera com recuo crescente e limitado, registra a falha e não consome CPU cheia.
- AC-4: Dado `accept` que volta a funcionar após falhas, quando um cliente conecta, então é atendido e o recuo reinicia.
- AC-5: Dado o daemon já rodando, quando o `main` roda, então sai com código 1 e mensagem em stderr.
- AC-6: Dado `XDG_RUNTIME_DIR` ausente, quando o `main` roda, então sai com código 1 e mensagem em stderr.
- AC-7: Dado a lógica de partida, quando testada, então roda sem `System.exit` (só o `main` fino sai), com tempo controlado por `Clock`/sem `sleep` real.

**Fora do escopo:** comandos além de `ping`; mudar o protocolo; supervisão ou reinício automático do daemon.

**Aderência ao produto:** serve "um daemon por máquina, único escritor do estado" (Operating Context) e o log só com ids, sem payload. Não contradiz posicionamento nem o que está fora do escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon` (sem mudança de contrato público nem de protocolo; sem migração).

- `SocketServer.start(path, log: DaemonLog = StderrDaemonLog, pause: (Long) -> Unit = Thread::sleep)`: o `log` e a pausa entram por parâmetro para os testes não usarem relógio real.
- Lock de partida: `prepareDirectory` → abre `daemon.lock` (`0600`) no diretório `0700` e faz `FileChannel.tryLock()` antes de `clearOrphan`. Lock não obtido (`null` ou `OverlappingFileLockException`, caso de dois starts no mesmo processo) → `DaemonAlreadyRunningException`. O lock vive até `close()`, que o libera; o arquivo de lock não é apagado (apagar abriria corrida de inode).
- `DaemonLog` (`fun interface`, `log(event: String, fields: Map<String, String>)`) e `StderrDaemonLog`, que imprime `event=<e> key=value ...`. Só tipo do erro (`javaClass.simpleName`), nunca mensagem nem conteúdo do cliente. O daemon não tem card; não há id a registrar.
- `serve`: os dois `catch` passam a chamar `log.log("client_error", mapOf("error" to e.javaClass.simpleName))`.
- `acceptLoop`: extraído para `AcceptLoop(accept: () -> SocketChannel, ...)` (internal) para testar com um `accept` falho. Em `IOException` não fechada: registra `accept_failed` com `error` e `attempt`, pausa `acceptBackoffMillis(attempt)` (puro: 10 ms dobrando até 1000 ms) e tenta de novo; um `accept` bem-sucedido zera `attempt`.
- `Main.kt`: lógica em `launchDaemon(env: Map<String, String>, err: PrintStream): DaemonLaunch` (`sealed`: `Started(server)` / `Failed(exitCode = 1)`), que escreve a mensagem em `err`. `main` só chama `launchDaemon` e `exitProcess` (fino, não testado).

Arquivos: `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`, `Main.kt`, `DaemonLog.kt` (novo); testes em `SocketServerTest.kt`, `MainTest.kt` não é criado (ver Decisions).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModuleTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModuleTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Log por interface injetável com saída em stderr, sem biblioteca nova: o projeto não tem lib de log e CLAUDE.md pede dependência nova só com versão fixa no catálogo; o fake torna o log observável nos testes.
- Pausa e recuo por parâmetro, não `Clock`: o recuo é duração, não instante; o fake registra as pausas pedidas sem `sleep` real.
- Testes nos arquivos existentes `SocketServerTest.kt` e `ModuleTest.kt` (este último cobre o módulo/`Main`); nenhum arquivo de teste novo.
- Lock em arquivo separado `daemon.lock` e não no próprio socket: o socket é apagado e recriado, o que invalidaria o lock.
- Peso `heavy` pela regra (7 ACs, mais de 6), embora a issue esperasse `standard`: uma camada e 3 arquivos; o dono pode baixar para `standard` no Spec Review.

## Open questions
- none

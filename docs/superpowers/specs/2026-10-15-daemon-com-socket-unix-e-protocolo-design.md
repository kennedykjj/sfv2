# Daemon com socket unix e protocolo — spec

**Issue:** #15 · **Branch:** `feat/15-daemon-com-socket-unix-e-protocolo` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Só o daemon escreve o estado; app e CLI precisam de um canal local para falar com ele, sem abrir porta TCP.
**Audience:** O desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de uma empresa), que roda um daemon por máquina e o usa pela CLI e pelo app.
**Acceptance criteria**
- **AC-1** — Given o daemon iniciado, when um cliente envia `ping` pelo socket unix, then recebe `pong`.
- **AC-2** — Given um daemon já rodando, when um segundo é iniciado, then o segundo sai com erro (código diferente de zero, mensagem clara) e o primeiro continua atendendo.
- **AC-3** — Given o daemon rodando, when um cliente envia uma linha que não é JSON válido ou um comando desconhecido, then recebe uma resposta de erro e o daemon continua atendendo outros clientes.
- **AC-4** — Given o protocolo, when o cliente envia uma mensagem, then ela é um objeto JSON em uma linha e a resposta também é um objeto JSON em uma linha.
- **AC-5** — Given `XDG_RUNTIME_DIR` definido, when o daemon inicia, then o socket é criado dentro dele, acessível só ao usuário dono.
- **AC-6** — Given um socket órfão de um daemon que caiu, when um novo daemon é iniciado, then ele assume o socket e atende normalmente.
- **AC-7** — Given o daemon encerrado de forma normal, when ele termina, then o socket é removido.
**Out of scope:** Windows (fase 8); porta TCP; autenticação além das permissões do socket; comandos de domínio (cards, eventos) além de `ping`; reconexão automática de clientes.
**Product fit:** Serve "o daemon é o único escritor; app e CLI são clientes por socket local" (Operating Context). Não contradiz posicionamento nem o fora de escopo. Roadmap fase 1, S1-08.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contrato (protocolo, uma mensagem JSON por linha, UTF-8): pedido `{"cmd":"ping"}`; resposta de sucesso `{"ok":true,"result":"pong"}`; erro `{"ok":false,"error":"<mensagem>"}` (JSON inválido, `cmd` ausente ou comando desconhecido). Linha acima de 64 KiB é erro e a conexão é fechada.
- Camada `daemon`, só arquivos novos em `daemon/src/main/kotlin/sfv2/daemon/`:
  - `Protocol.kt`: `parseRequest(line)` e `render(response)`, funções puras (sem IO), com `Response` selada (`Pong`, `Error`).
  - `SocketPaths.kt`: `resolveSocketPath(env: Map<String, String>)` = `$XDG_RUNTIME_DIR/sfv2/daemon.sock`; sem `XDG_RUNTIME_DIR` falha com `DaemonStartException` dizendo a variável ausente.
  - `SocketServer.kt`: `UnixDomainSocketAddress` do JDK (`ServerSocketChannel`), uma thread virtual por conexão. No início cria o diretório com permissão `rwx------` e o socket `rw-------`; se o arquivo já existe, tenta conectar: se responde há outro daemon vivo e lança `DaemonAlreadyRunningException(path)`; se recusa, o socket é órfão: apaga e assume. `close()` encerra o servidor e remove o socket.
  - `Main.kt`: `main` que sobe o servidor, trata as duas exceções com mensagem em stderr e exit 1, e fecha o servidor num shutdown hook.
- Sem mudança em `core`, `store`, `ports`, `adapters` e `cli`: o cliente do socket (`sfv2 ping`) fica para outro card. Sem migração; `ARCHITECTURE.md` não muda (fronteiras intactas).
- Dependência nova: `jackson-databind` 2.19.2 em `gradle/libs.versions.toml` (versão fixa) e em `daemon/build.gradle.kts`.
- Arquivos: `Protocol.kt`, `SocketPaths.kt`, `SocketServer.kt`, `Main.kt`, `SocketServerTest.kt`, `gradle/libs.versions.toml`, `daemon/build.gradle.kts`.

## Test plan
_Tech Lead_

Testes em processo: o servidor sobe num diretório temporário passado como `XDG_RUNTIME_DIR` e o cliente é um `SocketChannel` do JDK; sem processo externo.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Nenhum teste existente cobre o socket (`ModuleTest.kt` só testa o nome do módulo): arquivo novo `SocketServerTest.kt`, um só para todos os ACs.
- JSON com jackson-databind e não parser manual: `kotlinx-serialization` exige plugin em `build-logic/`, que a fábrica não altera.
- Socket em `$XDG_RUNTIME_DIR/sfv2/daemon.sock` (subdiretório 0700): a issue só fixa o diretório. Sem `XDG_RUNTIME_DIR` o daemon recusa iniciar em vez de cair em /tmp, que é compartilhado.
- Socket órfão detectado por tentativa de conexão, não por PID file: não cria segundo arquivo de estado.
- Peso `heavy` (a issue esperava `standard`): a spec tem 7 critérios, acima do limite de 6 para `standard`.
- PO: AC-4 a AC-7 acrescentados à issue (protocolo, local do socket, socket órfão, limpeza), pois decorrem das notas e tornam AC-1 e AC-2 verificáveis.

## Open questions
- none

# Transporte TCP local com token — spec

**Issue:** #78 · **Branch:** `feat/78-transporte-tcp-local-com-token` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** o daemon só atende em socket unix. O JDK não tem named pipe, então o Windows (decisão 11) precisa de um transporte TCP local. Um listener TCP, ao contrário do socket 0600, é alcançável por qualquer processo da máquina; sem autenticação própria, qualquer usuário local falaria com o daemon.
**Audience:** o dono (clientes CLI e app em sistemas sem socket unix, a partir da fase 8) e quem mantém o daemon.
**Acceptance criteria**
- **AC-1** — Given o daemon iniciado com `SFV2_TCP_PORT` definido, when ele sobe, then ouve em `127.0.0.1` (nunca em outra interface) na porta pedida (`0` = porta livre), e grava a porta efetiva em `tcp.port`, ao lado do socket.
- **AC-2** — Given uma conexão TCP, when a primeira linha é `{"cmd":"auth","token":"<token>"}` com o token certo, then o daemon responde `{"ok":true,"result":"authenticated"}` e os comandos seguintes na mesma conexão (ex.: `ping`) funcionam como no socket unix.
- **AC-3** — Given uma conexão TCP, when a primeira linha tem token ausente, vazio ou errado, ou é qualquer outro comando, then o daemon responde um erro fixo (igual nos três casos, sem ecoar o token), fecha a conexão e não executa nada.
- **AC-4** — Given uma conexão TCP sem autenticar, when passam 5 s sem a linha de `auth` ou a linha passa de 1 KiB, then o daemon fecha a conexão sem executar nada.
- **AC-5** — Given o daemon iniciado, when o transporte TCP é criado, then o token é gerado a cada partida (32 bytes aleatórios em hex), gravado em `tcp.token` com permissão 0600 no diretório 0700, e nunca aparece em resposta, log, evento ou `toString`.
- **AC-6** — Given uma conexão TCP autenticada, when ela pede um comando de dono (`card.move` como dono, `project.add`) sem `proof` válido, then é recusado como no socket unix: o token do transporte autentica a conexão, não vira prova do dono.
- **AC-7** — Given `SFV2_TCP_PORT` ausente, when o daemon sobe, then não abre listener TCP nem cria `tcp.token` e `tcp.port`, e o socket unix se comporta como antes.
- **AC-8** — Given o daemon encerrado, when `close()` roda, then o listener TCP e as conexões TCP são fechados e `tcp.port` é removido.
**Out of scope:** trocar CLI, app e doctor para falar TCP (card próprio, depende deste); serviço no macOS/Windows e CI (#79, #80, adiados pela decisão 21); TLS; permissão de arquivo no Windows (hoje só POSIX).
**Product fit:** destrava a fase 8 sem tocar o caminho do Linux, que segue no socket unix; o TCP é opt-in.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- `daemon/src/main/kotlin/sfv2/daemon/TcpTransport.kt` (novo): abre `ServerSocketChannel` INET em `InetAddress.getLoopbackAddress()` (127.0.0.1), cria o token com `OwnerProof.create(path, random)` (já gera 32 bytes, 0600, comparação em tempo constante), grava `tcp.port` e expõe `authenticate(client): Boolean`, que lê a primeira linha (máx. 1 KiB) com prazo de 5 s e compara o token. Resposta de falha fixa e idêntica nos casos.
- `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: `start` ganha `tcp: TcpTransport? = null`; um segundo `AcceptLoop` (já recebe `SocketChannel`) chama `authenticate` antes de `serve(client)`; `close()` fecha o listener TCP e apaga `tcp.port`. O protocolo e o `dispatch` não mudam; `serve` é reaproveitado.
- `daemon/src/main/kotlin/sfv2/daemon/Main.kt`: lê `SFV2_TCP_PORT` do `env` e monta o `TcpTransport` ao lado do socket; valor inválido (não numérico, fora de 0..65535) falha a partida com `DaemonStartException` dizendo o que aconteceu e como corrigir.
- Sem mudança de banco, de evento ou de ferramenta MCP; o protocolo do socket unix mantém a forma atual (o comando `auth` só existe no TCP).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModuleTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_

**Ativo e ameaça:** o daemon é a única porta de escrita do estado. Num listener TCP, outro usuário ou processo local (ou página web via requisição a `localhost`) tenta falar com ele. Mitigações:
1. Bind só em loopback (127.0.0.1); nunca `0.0.0.0` nem configurável.
2. Autenticação obrigatória na primeira linha; token de 32 bytes aleatórios, comparação em tempo constante, novo a cada partida, arquivo 0600 em diretório 0700. Quem lê o arquivo já é o dono do usuário.
3. Sem token válido nada é executado; erro fixo e igual para ausente, vazio e errado (sem oráculo); o token não é ecoado.
4. Conexão não autenticada tem prazo de 5 s e 1 KiB, para não segurar o daemon (slowloris).
5. O token do transporte não é prova do dono: comandos de dono seguem exigindo `proof` (AC-6).
6. Uma página web no navegador não fala o protocolo (não envia a linha de `auth` com o token); o token nunca vai em URL, log ou evento.
7. Log só com `cmd` e motivo curto (`auth_failed`), nunca com token ou conteúdo da linha.

## Rollback
Opt-in: sem `SFV2_TCP_PORT` o código novo não roda. Para desfazer, remover a variável do `service.env` e reiniciar o daemon; `tcp.token` e `tcp.port` são recriados ou apagados a cada partida. Nada migra.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Opt-in por `SFV2_TCP_PORT`: o Linux não precisa do TCP e um listener a mais é superfície de ataque que não deve existir por padrão.
- Reuso de `OwnerProof.create` para o token do transporte: já cobre geração, 0600, tempo constante e `toString` mascarado; arquivo `tcp.token` separado de `owner.token` para o token do transporte nunca valer como prova do dono.
- Sem arquivo de teste novo: `SocketServerTest.kt` é a suíte do transporte e `ModuleTest.kt` a da partida do daemon.
- Prazo de 5 s do `auth` implementado com fechamento do canal por um temporizador, não por `soTimeout`, porque `Channels.newInputStream` pode ignorar o timeout (suposição, confiança média: o desenvolvimento confirma com o teste de AC-4).
- Peso `heavy` porque `## Security` e `## Rollback` têm conteúdo.
- Clientes (CLI, app, doctor) ficam fora: o card é a ponta do daemon; trocar o cliente exige descoberta de porta e token e vira card próprio.

## Open questions
- none

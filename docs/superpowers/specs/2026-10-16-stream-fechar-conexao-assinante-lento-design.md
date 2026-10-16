# Stream: fechar a conexão do assinante lento que nunca lê — spec

**Issue:** #122 · **Branch:** `feat/122-stream-fechar-conexao-assinante-lento` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** no stream de eventos (#17), um assinante que nunca lê nem desconecta é removido da lista (`dropped`), mas sua conexão, thread e descritor continuam abertos. Vários clientes travados esgotam o limite de arquivos do daemon, que precisa ficar sempre ligado.

**Público:** o desenvolvedor sozinho com muitos projetos (app, CLI e agentes como clientes do daemon local, que serve vários ao mesmo tempo).

**Critérios de aceitação**
- AC-1: Dado um assinante removido por lentidão que não lê, quando passa o prazo de cortesia, então o daemon fecha a conexão e libera a thread e o descritor, sem o cliente ler ou desconectar.
- AC-2: Dado um assinante removido por lentidão que ainda consegue ler dentro do prazo de cortesia, quando o daemon vai fechar, então ele recebe `{"error":"subscriber too slow"}` antes do fechamento.
- AC-3: Dado um assinante removido por lentidão que lê e consome tudo antes do prazo, quando o prazo passa, então a conexão também é fechada e não fica aberta.
- AC-4: Dado vários assinantes lentos removidos, quando todos os prazos passam, então o número de descritores e threads do daemon volta ao nível anterior à conexão deles.
- AC-5: Dado um assinante saudável conectado, quando outros assinantes lentos são fechados, então ele continua recebendo eventos sem interrupção.
- AC-6: Dado o prazo de cortesia configurável, quando os testes o controlam por relógio injetado, então o fechamento ocorre sem espera real.

**Fora do escopo:** mudar o protocolo do stream; política de reconexão do cliente; alterar a regra que decide quando um assinante é lento (#17).

**Aderência ao produto:** serve ao daemon sempre ligado como único escritor do estado, que trabalha com clientes de qualquer tipo sem degradar. Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contratos: nenhum muda. `EventStream` ganha os parâmetros `clock: Clock = Clock.systemUTC()` e `closeGrace: Duration = DEFAULT_CLOSE_GRACE` (construtor) e a função `sweep()` (internal, para teste). O protocolo do stream e o `{"error":"subscriber too slow"}` ficam como estão.
- Camada: só `daemon`. Arquivos: `daemon/src/main/kotlin/sfv2/daemon/EventStream.kt`, `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt` (repassa `clock` e `closeGrace` ao construir o `EventStream`), `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt`.
- Mecanismo: ao marcar `dropped`, o assinante registra `droppedAt = clock.instant()`. `sweep()` percorre os assinantes removidos por lentidão (guardados numa lista própria, já que saem de `subscribers`) e, para cada um com `clock.instant() >= droppedAt + closeGrace`, chama `finish()`: fecha o canal (isso interrompe o `write` bloqueado do escritor com `IOException`), remove da lista e roda `onClosed`. Uma thread virtual do `EventStream` chama `sweep()` a cada `POLL_MILLIS`; ela morre com um `close()` do `EventStream`, ou fica como thread de daemon.
- Quem ainda lê: o escritor desbloqueia sozinho, envia o erro e chama `finish()` (já é o comportamento atual), então o `sweep()` encontra o assinante já encerrado e não faz nada (`finish()` idempotente).
- Sem migração nem rollback.
- `DEFAULT_CLOSE_GRACE` = 5 s.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Prazo de cortesia de 5 s por padrão: longo o bastante para um cliente lento ler o erro, curto para não segurar descritor; é parâmetro.
- `sweep()` interno e chamado direto nos testes com um `Clock` mutável, em vez de esperar a thread de varredura: determinístico, sem `sleep` real.
- Os testes entram em `SocketServerTest.kt`, que já cobre o stream; contar descritores via `/proc/self/fd` (Linux) no AC-4.
- Peso `standard`: uma camada e 3 arquivos, mas 6 critérios (acima do limite de 3 do `light`).

## Open questions
- none

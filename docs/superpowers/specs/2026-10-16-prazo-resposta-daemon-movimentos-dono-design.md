# Dar prazo à resposta do daemon nos movimentos do dono do app — spec

**Issue:** #222 · **Branch:** `feat/222-prazo-resposta-daemon-movimentos-dono` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** `SocketOwnerCommands.send` (`app/src/main/kotlin/sfv2/app/OwnerCommands.kt`) lê a resposta do daemon com `readLine()` bloqueante e sem prazo. Se o daemon aceita a conexão mas não responde, o movimento do dono fica pendente para sempre: o card permanece na coluna otimista e nenhum aviso aparece. `SocketCardDetailSource` já tem prazo (5 s); o caminho do movimento não.
**Audience:** o dono, ao mover cards no quadro do app.
**Acceptance criteria**
- **AC-1** — Given um daemon que aceita a conexão mas não responde dentro do prazo, when o dono envia um movimento, then `SocketOwnerCommands.move` retorna `MoveOutcome.NotSent(NotSentCause.NO_ANSWER)` dentro do prazo configurado e fecha a conexão.
- **AC-2** — Given um daemon que responde dentro do prazo, when o dono envia um movimento, then o resultado é o mesmo de antes (`Accepted` ou `Refused` com o motivo do daemon), sem regressão.
- **AC-3** — Given que o movimento ficou sem resposta (`NO_ANSWER`), when o controller recebe o resultado, then o card volta à coluna de origem e o aviso `NOT_SENT` diz que não houve resposta do daemon e que o dono deve conferir o quadro, pois o movimento pode ter sido aplicado.
- **AC-4** — Given um daemon que não responde, when `move` é chamado, then a conexão recusada ou o token ausente continuam com as causas `DAEMON_UNAVAILABLE` e `NO_OWNER_PROOF` (o prazo não muda esses caminhos e, sem token, nada é enviado).
**Out of scope:** retry automático do movimento; prazo configurável pelo dono nas configurações; prazo em outros clientes do socket (feed, settings, probe).
**Product fit:** o app é o painel do dono; um movimento que trava em silêncio quebra a confiança no quadro. O aviso honesto ("sem resposta, confira o quadro") preserva a regra de que o daemon é o juiz.
**Touches UI:** yes

## UX
_UX/UI Designer_

Sem mudança de layout nem de componente: reaproveita o aviso `NOT_SENT` já existente, com um texto novo em português: "Movimento sem resposta do daemon: confira o quadro antes de repetir." O texto diz o que aconteceu e como resolver, sem sugerir que nada foi aplicado.

## Architecture
_Tech Lead_

Camada `app`, três arquivos:
- `app/src/main/kotlin/sfv2/app/OwnerCommands.kt`: `SocketOwnerCommands` ganha `timeoutMillis: Long = 5_000` no construtor. `send` passa a ler a resposta numa `CompletableFuture` com `get(timeoutMillis)`, como em `SocketCardDetailSource` (`DaemonClient.kt`); o `finally` fecha o canal, o que encerra a leitura bloqueante. `TimeoutException` vira `MoveOutcome.NotSent(NotSentCause.NO_ANSWER)`; as demais falhas de IO continuam `DAEMON_UNAVAILABLE`.
- `app/src/main/kotlin/sfv2/app/OwnerMoves.kt`: `NotSentCause` ganha `NO_ANSWER`. O controller não muda: o ramo `NotSent` já reverte o movimento otimista e mostra o aviso.
- `app/src/main/kotlin/sfv2/app/Theme.kt`: `Texts.notSent` ganha o ramo `NO_ANSWER` (o `when` exaustivo obriga).

O token continua lido a cada envio e nunca guardado. Nenhuma mudança no protocolo do socket, no daemon nem no core.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Causa nova `NO_ANSWER` em vez de reaproveitar `DAEMON_UNAVAILABLE`: o texto precisa avisar que o movimento pode ter sido aplicado, o que "indisponível" não diz.
- Em `NO_ANSWER` o card volta à coluna de origem, como nos demais `NotSent`: o monitor de eventos do daemon reconcilia o quadro se o movimento tinha sido aplicado (já é assim em "a refusal does not overwrite the column a daemon event already took the card to").
- Prazo de 5 s fixo, igual ao `SocketCardDetailSource`: consistência e sem nova configuração.
- Testes no `OwnerMovesTest.kt` existente, que já tem o `FakeDaemon`; o teste de sem resposta usa um `FakeDaemon` que não responde (reply bloqueado por latch, sem `Thread.sleep`) e um `timeoutMillis` curto no construtor.
- `Touches UI: yes` só pelo texto do aviso; sem mudança visual, por isso sem captura nova de layout.

## Open questions
- none

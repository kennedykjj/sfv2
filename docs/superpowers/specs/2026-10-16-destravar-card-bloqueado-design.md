# Destravar card bloqueado: sfv2 card unblock e bloqueados visíveis — spec

**Issue:** #272 · **Branch:** `feat/272-destravar-card-bloqueado` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Um card com `CARD_BLOCKED` só volta a rodar se o dono o mover de coluna. Quando o bloqueio vem de limite de custo, de porta de entrega recusada ou de veredito `BLOCKED`, o card já está na coluna certa e o dono não tem um movimento válido a fazer: ele fica parado sem saída. Além disso, `sfv2 card list` não diz quais cards estão bloqueados; o dono só descobre pelo pulso da fábrica, que cita o card sem dizer que ele é o parado.
**Audience:** o dono da fábrica, operando pela CLI.
**Acceptance criteria**
- **AC-1** — Given um card com bloqueio aberto (último `CARD_BLOCKED` sem movimento do dono nem aprovação depois), when o dono grava um `CARD_UNBLOCKED`, then o bloqueio deixa de estar aberto e o estado do assistente deixa de ser `BLOCKED` por causa dele.
- **AC-2** — Given um card bloqueado, when o dono envia `card.unblock` com a prova válida, then o daemon grava um evento `CARD_UNBLOCKED` com ator dono na mesma transação, a coluna do card não muda e a resposta traz o card.
- **AC-3** — Given um card que não está bloqueado, um id inexistente, um ator que não é o dono ou uma prova ausente ou inválida, when `card.unblock` é enviado, then o daemon recusa com mensagem que diz o que houve e o id, e não grava evento.
- **AC-4** — Given um card bloqueado por custo, when o dono o destrava, then o `CostBudget` volta a permitir o lançamento de runs para esse card (o dispatcher deixa de pulá-lo).
- **AC-5** — Given cards bloqueados e não bloqueados, when o cliente envia `card.list` ou `card.show`, then cada card traz o campo `blocked` (verdadeiro só quando há bloqueio aberto), sem alterar os campos anteriores.
- **AC-6** — Given um daemon rodando, when o dono roda `sfv2 card unblock <id>`, then a CLI envia `card.unblock` com a prova do dono, imprime a linha do card e sai com 0; em recusa do daemon, imprime a mensagem e sai com 1.
- **AC-7** — Given cards bloqueados, when o dono roda `sfv2 card list`, then a linha de cada card bloqueado termina com `BLOQUEADO` e as demais linhas ficam como antes.
**Out of scope:** botão ou ação de destravar no app (a tela do card fica para outro card); subir o limite de custo ou limpar o custo gasto; mostrar o motivo do bloqueio na lista; mudar a regra de que mover o card ou aprovar também destrava.
**Product fit:** o dono decide, e a decisão fica como evento assinado por ele; a fábrica não se destrava sozinha.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camadas tocadas: `core`, `daemon`, `cli`. Nenhuma migração: o tipo do evento é guardado pelo nome (`EventJson` lê `EventType.entries`), então a coluna do banco não muda.

- `core/src/main/kotlin/sfv2/core/Event.kt`: novo `EventType.CARD_UNBLOCKED`.
- `core/src/main/kotlin/sfv2/core/AssistantState.kt`: `isOwnerUnblock` passa a aceitar `CARD_UNBLOCKED` com ator dono; `hasOpenBlock` vira público (`AssistantStates.hasOpenBlock(events)`) para o daemon marcar `blocked` pela mesma regra do assistente, sem duplicá-la.
- `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `Request.CardUnblock(actor, id, proof)` e o parse de `card.unblock`; `CardView` ganha `blocked: Boolean = false` e a serialização de `CardResult`/`CardsResult` emite `blocked`. Campo novo e aditivo: clientes antigos o ignoram.
- `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt`: `unblock(request)` (prova do dono, card existe, bloqueio aberto; grava `CARD_UNBLOCKED` via `recorder.record`, coluna intacta) e `blocked` nas views de `card.list`/`card.show`. O `when` exaustivo de `handle` ganha o novo caso.
- `daemon/src/main/kotlin/sfv2/daemon/CostBudget.kt`: `isBlocked` passa a tratar um `CARD_UNBLOCKED` do dono posterior ao último `CARD_BLOCKED` como desbloqueio (via `store.lastSeq(cardId, CARD_UNBLOCKED, Actor.Owner)`).
- `cli/src/main/kotlin/sfv2/cli/CardCommands.kt`: `CardUnblock` registrado em `withCardCommands`; `cardLine` acrescenta `  BLOQUEADO` quando `blocked` é verdadeiro.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-7 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |

## Security
_Cybersec_

- `card.unblock` exige ator dono e a prova do dono (`ownerProof.matches`), como `card.move` e `card.approve`; sem isso, recusa sem gravar e loga só `cmd` e `card`.
- O destravamento é um evento assinado pelo dono; nenhum agente tem caminho para gravá-lo (o ator `agent:*` é recusado, coberto por AC-3). O MCP não ganha ferramenta nova.
- `blocked` é um booleano: o motivo do bloqueio pode vir de texto de agente e por isso não sai na lista.
- Logs só com ids; nenhum payload nem texto de issue ou spec.

## Rollback
O código antigo lança `UnknownEventTypeException` ao ler um `CARD_UNBLOCKED`. Reverter o merge é seguro enquanto nenhum destravamento tiver sido gravado; depois disso, a reversão exige antes ter o daemon novo rodando só até o fim do uso, ou aceitar que o daemon antigo recuse a leitura desse card. Não há migração a desfazer.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Evento novo `CARD_UNBLOCKED` em vez de reaproveitar `CARD_MOVED` com coluna igual: `MoveRules` não admite mover para a mesma coluna, e um movimento falso enganaria o histórico e o tempo por coluna.
- A coluna não muda ao destravar: o bloqueio acontece na coluna em que o trabalho parou, e o dispatcher retoma dali.
- A regra de bloqueio aberto continua uma só no `core` (`hasOpenBlock`); o `CostBudget` mantém a consulta por `lastSeq` que já usa e ganha só a checagem do novo evento, para não reescrever o caminho quente do dispatcher.
- `blocked` entra em `CardView` como campo aditivo com padrão `false`, mantendo a forma anterior do protocolo do socket.
- Sem arquivo de teste novo: as suítes `AssistantStateTest`, `SocketServerTest`, `RunCostTest` e `MainTest` já cobrem os módulos.
- Peso `heavy`: três camadas tocadas e `## Security` e `## Rollback` com conteúdo.
- Destravar um card bloqueado por custo não zera o gasto: se o limite continuar atingido, o próximo fim de run bloqueia o card de novo. É o comportamento certo (o dono vê o limite e decide subi-lo), e fica registrado aqui para não surpreender.

## Open questions
- none

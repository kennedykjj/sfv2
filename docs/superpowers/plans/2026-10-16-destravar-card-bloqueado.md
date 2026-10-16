# Destravar card bloqueado — plano

Spec: `docs/superpowers/specs/2026-10-16-destravar-card-bloqueado-design.md` (#272).

1. **core**: acrescentar `CARD_UNBLOCKED` a `EventType`; fazer `isOwnerUnblock` aceitá-lo com ator dono; tornar `AssistantStates.hasOpenBlock` público. Teste em `AssistantStateTest` (AC-1).
2. **daemon, protocolo**: `Request.CardUnblock`, parse de `card.unblock`, campo `blocked` em `CardView` e na serialização.
3. **daemon, comando**: `CardCommands.unblock` (prova, card existe, bloqueio aberto, grava `CARD_UNBLOCKED`, coluna intacta); `blocked` em `card.list` e `card.show`. Testes em `SocketServerTest` (AC-2, AC-3, AC-5).
4. **daemon, custo**: `CostBudget.isBlocked` reconhece o `CARD_UNBLOCKED` do dono. Teste em `RunCostTest` (AC-4).
5. **cli**: `CardUnblock` registrado em `withCardCommands`; `cardLine` marca `BLOQUEADO`. Testes em `MainTest` (AC-6, AC-7).
6. Rodar `./gradlew :core:test :daemon:test :cli:test` e depois `./gradlew build`.

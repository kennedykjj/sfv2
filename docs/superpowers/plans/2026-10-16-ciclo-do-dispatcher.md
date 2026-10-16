# Plano: ciclo do dispatcher (#27)

Spec: `docs/superpowers/specs/2026-10-16-ciclo-do-dispatcher-design.md`

## Tarefas

Todas na camada `daemon`, agente `engineer` (peso heavy: chamada de agente).

1. **RunPool** — `daemon/src/main/kotlin/sfv2/daemon/RunPool.kt`: novos `isLocked(cardId)` e `hasRoom()`, ambos `@Synchronized`.
2. **EventStream** — `daemon/src/main/kotlin/sfv2/daemon/EventStream.kt`: `addListener(listener)`; `publish` chama os ouvintes sem bloquear e isola exceções (os demais assinantes seguem, a gravação não falha).
3. **Dispatcher** — novo `daemon/src/main/kotlin/sfv2/daemon/Dispatcher.kt`: `DispatchStage`, `LaunchedRun`, `StageLauncher`, `PauseSwitch`/`FilePauseSwitch`, `WorkSchedule`/`AlwaysWorkTime`, `DispatchLimits`, `DispatchedRun`, `Dispatcher` (`runCycle`, `wake` sem lock, `start`/`close`, `onEvent`, recuo crescente 60 s até 30 min por card, `startedAt` vindo do launcher).

Dependências: 1 e 2 antes de 3.

## Testes (QA)

`daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` (AC-1 a AC-10), com fakes de `StageLauncher`, `PauseSwitch` e `WorkSchedule` e `Clock` fixo.

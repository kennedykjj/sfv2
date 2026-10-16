# Pipeline de validate — plano (#41)

Camada única: `daemon` (agente: engineer, feito solo pelo Tech Lead; peso `standard`).

## Tarefas

1. **daemon — `BranchSync`** (`WorktreeGit.kt`): interface `BranchSync.mergeMain(worktree)` com resultado `Merged | Conflict | Failed`; implementação `SystemBranchSync` (git sem shell, hooks desligados, identidade fixa, `merge --abort` no conflito). Sem dependências.
2. **daemon — `ValidatePipeline` e `ValidateStageLauncher`** (`ValidatePipeline.kt`, novo): fluxo da spec, reaproveitando `RefinePipeline` como molde. Depende de 1.
3. **daemon — `StageRouter`** (`Dispatcher.kt`): delegação por `DispatchStage`. Sem dependências.
4. **daemon — montagem** (`RefineRuntime.kt`): `ValidatePipeline`, `ValidateStageLauncher`, `StageRouter`, `stages = setOf(REFINE, VALIDATE)`. Depende de 2 e 3.

## Arquivos

- `daemon/src/main/kotlin/sfv2/daemon/WorktreeGit.kt`
- `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt` (novo)
- `daemon/src/main/kotlin/sfv2/daemon/Dispatcher.kt`
- `daemon/src/main/kotlin/sfv2/daemon/RefineRuntime.kt`
- `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` (QA)

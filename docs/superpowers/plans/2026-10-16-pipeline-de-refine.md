# Pipeline de refine — plano

**Issue:** #39 · **Spec:** `docs/superpowers/specs/2026-10-16-pipeline-de-refine-design.md` · **Peso:** standard

Uma camada (`daemon`), executada pelo Tech Lead sem agente `engineer`.

## Tarefas

### T1 — daemon: `RefinePipeline` e `RefineStageLauncher` (AC-1 a AC-5, AC-7, AC-8)
Arquivo novo `daemon/src/main/kotlin/sfv2/daemon/RefinePipeline.kt`:

- `data class GateResult(val name: String, val passed: Boolean, val reason: String?)`.
- `fun interface RefineGates { fun evaluate(cardId: String, worktree: Path): List<GateResult> }` — implementação real `SpecScopeRefineGates` (mesmo arquivo): lê a spec alterada no worktree contra `main` (`ChangedPaths`), roda `SpecGates.check` (`test-plan`, `weight`) e `ScopeGate.check` (`scope`, camadas vazias) e grava um `GATE_FINISHED` (ator `Factory("gate")`) por gate pelo `EventRecorder`.
- `class RefinePipeline(project, store, recorder, worktrees, sessions, mcpPort, runtime, costRecorder, gates, configDir, clock, model, maxRounds = 3, log)` com `fun run(cardId: String)`:
  1. `READY_FOR_REFINEMENT → REFINEMENT` (`CARD_MOVED`, ator `Factory("refine")`), se o card ainda está em Ready for Refinement.
  2. `worktrees.ensure`; por rodada: `sessions.open(project.repo, "refine", cardId)`; escreve o `mcp-config` JSON em `configDir` (fora do worktree), permissão 0600; `runtime.launch(SessionRequest("refine", worktree, model, config))`; `RunCostRecorder.record`; em `finally`: apaga o arquivo e `sessions.close(token)`. O token nunca vai a log nem a evento.
  3. `gates.evaluate`; todos verdes → `REFINEMENT → SPEC_REVIEW`; algum vermelho e rodadas restantes → nova rodada; esgotadas → `CARD_BLOCKED` (ator `Factory("refine")`, payload `reason` = `gates_failed: <nomes>`), sem novo run.
- `class RefineStageLauncher(pipelineFor: (String) -> RefinePipeline, pool: RunPool, ...) : StageLauncher`: `REFINE` inicia `run` numa thread própria, devolve `LaunchedRun(pid do daemon, início do processo)`; `finally` da thread chama `pool.release(cardId)`; outro estágio → `IllegalArgumentException`.

### T2 — daemon: `Dispatcher` filtra estágios (AC-6)
`Dispatcher(... stages: Set<DispatchStage> = DispatchStage.entries.toSet())`; o ciclo ignora estágios fora do conjunto.

### T3 — daemon: ligação no `Main.kt`
`launchDaemon` monta o `Dispatcher` com `stages = setOf(DispatchStage.REFINE)` e o `RefineStageLauncher`, e o inicia junto com o socket.

## Dependências
T1 e T2 independentes; T3 depende de T1 e T2. Testes de aceitação (QA, antes): `RefinePipelineTest.kt` (novo) e `DispatcherTest.kt` (AC-6).

## Arquivos
`daemon/src/main/kotlin/sfv2/daemon/RefinePipeline.kt` (novo), `Dispatcher.kt`, `Main.kt`.

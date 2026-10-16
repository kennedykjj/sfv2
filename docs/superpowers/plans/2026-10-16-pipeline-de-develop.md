# Pipeline de develop — plano

**Issue:** #40 · **Spec:** `docs/superpowers/specs/2026-10-16-pipeline-de-develop-design.md`
**Camada única:** `daemon` (agente `engineer`). Tarefas em sequência, no mesmo worktree.

## Tarefas

### T1 — `ProjectLayer` com comandos de build/teste
- Owner: engineer · Camada: daemon · Depende de: nada
- `ProjectLayer` ganha `build: List<String>? = null`, `test: List<String>? = null`, `ran: String? = null`; `ProjectRegistry` lê os campos do `factory.yml` da `main`.
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/ProjectRegistry.kt` (e onde `ProjectLayer` estiver declarado).

### T2 — `PipelineStageLauncher` extraído
- Owner: engineer · Camada: daemon · Depende de: nada
- Extrai thread, espera do lock, `RunPool.release` no `finally` e gancho `onRunFinished` de `RefineStageLauncher`, sem mudar o comportamento dele.
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/RefinePipeline.kt`.

### T3 — `DevelopPipeline`, `DevelopGates`, `DevelopStageLauncher`
- Owner: engineer · Camada: daemon · Depende de: T1, T2
- Fluxo da spec: `READY_FOR_DEV` → `CODING` → worktree → por rodada: QA, gate vermelho, uma sessão `engineer` por camada, gates verdes + `AcceptanceEditGate` + `ScopeGate`, custo por sessão; tudo verde → `VALIDATION`; esgotadas as rodadas (`maxRounds`=3) → `CARD_BLOCKED` com `reason`.
- Modelo por rodada: `ModelStepUp.modelFor(cardId)`.
- `DevelopGates`: monta `GateSpec` de build/teste por camada; sem `test` → `no_test_command`; `{selector}` trocado pelo nome simples da classe (tabela Test plan da spec), só `[A-Za-z0-9_]`, senão `invalid_selector`; várias classes repetem `--tests` + marcador; sem shell.
- Sessão MCP com config fora do worktree, 0600, apagada e sessão revogada no fim (AC-7).
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/DevelopPipeline.kt` (novo).

### T4 — Fiação em `RefineRuntime`
- Owner: engineer · Camada: daemon · Depende de: T3
- Monta `DevelopStageLauncher`, `ModelStepUp` (ouvinte no `EventStream`, fechado em `close()`), `stages = setOf(REFINE, DEVELOP)` no `Dispatcher` com launcher que despacha por estágio.
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/RefineRuntime.kt`.

## Testes de aceitação (QA)
- `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` (novo): AC-1 a AC-5, AC-7, AC-8.
- `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` (existente): AC-6 com `DispatchStage.DEVELOP`.

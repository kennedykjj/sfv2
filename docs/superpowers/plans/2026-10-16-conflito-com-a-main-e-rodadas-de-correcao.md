# Conflito com a main e rodadas de correção — plano (#49)

Spec: `docs/superpowers/specs/2026-10-16-conflito-com-a-main-e-rodadas-de-correcao-design.md`

## Tarefas

1. **core** (engineer) — `core/`: linha `(READY_FOR_PROD to VALIDATION) to FACTORY_OR_OWNER` em `MoveRules.table`; `MoveRulesTest` acompanha. Sem dependência.
2. **daemon** (engineer) — `daemon/`, depende de 1:
   - `WorktreeGit.kt`: `mergeMainKeepingConflicts`, `abortMerge`, `concludeMerge` em `BranchSync`.
   - `ConflictResolution.kt` (novo): `ConflictLimits`, `ConflictOutcome`, `ConflictResolution.resolve` (rodadas por eventos `CARD_MOVED reason=conflict_round`, medição com tetos, sessão MCP, runtime com estágio `conflict`, custo via `RunCostRecorder`).
   - `AutonomousDelivery.kt` e `ValidatePipeline.kt`: usar `ConflictResolution` no conflito.
   - `Module.kt`: fiação.
   - `resources/skills/conflict/SKILL.md` (novo) com as três regras marcadas; contrato de skills (#36) cobre o estágio `conflict`.

## Testes de aceitação (QA)

`ConflictResolutionTest.kt` (AC-1, 2, 3, 5), `AutonomousDeliveryTest.kt` (AC-4), `ValidatePipelineTest.kt` (AC-6).

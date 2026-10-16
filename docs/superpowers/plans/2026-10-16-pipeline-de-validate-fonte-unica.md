# Pipeline de validate: fonte única de maxRounds e conversão dos gates — plano

**Issue:** #162 · **Spec:** `docs/superpowers/specs/2026-10-16-pipeline-de-validate-fonte-unica-design.md`

Uma só camada (`daemon`), peso standard; executado pelo Tech Lead (sem agente `engineer`).

## Tarefas

1. **daemon** — `daemon/src/main/kotlin/sfv2/daemon/ValidateGateResults.kt` (novo): `ValidateGateResults(results)` com `state` e `failedNames`; fábricas `fromExecutor`, `fromScope`, `fromSpec`; `const val DEFAULT_VALIDATE_MAX_ROUNDS = 3` com KDoc.
2. **daemon** — `ValidatePipeline.kt`: `ValidateGates.evaluate` devolve `ValidateGateResults`; `maxRounds` padrão usa a constante; `state` e motivo `gates_failed:` vêm do tipo.
3. **daemon** — `ProjectValidateGates.kt`: `layerGate`, `scopeGate` e `specGates` usam as fábricas; `GATE_FINISHED` inalterado.

Dependência: 1 → 2 → 3 (mesmo commit de código por compilação).

## Testes

QA escreve em `ValidatePipelineTest.kt` (AC-1..AC-5). A mudança de assinatura de `ValidateGates.evaluate` exige ajustar também os dublês em `ValidatePipelineTest.kt` e `e2e/src/test/kotlin/sfv2/e2e/PhaseExitTest.kt` (linha ~171), que hoje devolvem `List<GateResult>`.

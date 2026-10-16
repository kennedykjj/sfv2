# Gates de escopo e caminhos proibidos — plano

**Issue:** #30 · **Spec:** `docs/superpowers/specs/2026-10-16-gates-de-escopo-e-caminhos-proibidos-design.md`

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | daemon | engineer (solo, Tech Lead) | — | `daemon/src/main/kotlin/sfv2/daemon/ProjectRegistry.kt` (campo `forbidden` em `Project`, lido da main) |
| 2 | daemon | engineer (solo, Tech Lead) | 1 | `daemon/src/main/kotlin/sfv2/daemon/ScopeGate.kt` (novo: `ScopeGate`, `ScopeVerdict`) |
| 3 | daemon | engineer (solo, Tech Lead) | — | `daemon/src/main/kotlin/sfv2/daemon/GitChangedPaths.kt` (novo: `ChangedPaths`, `GitChangedPaths`) |

## Testes (QA, antes das tarefas)

- `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` (novo): AC-1, 2, 3, 4, 6, 7.
- `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt`: AC-5.

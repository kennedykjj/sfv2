# Estados do assistente derivados de eventos — plano

Issue #73. Spec: `docs/superpowers/specs/2026-10-16-estados-do-assistente-derivados-de-eventos-design.md`.

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | core | engineer | — | `core/src/main/kotlin/sfv2/core/AssistantState.kt` (`AssistantState`, `AssistantStateRules`, `AssistantCard`, `AssistantStates.derive`) |

QA escreve antes `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` (AC-1 a AC-8). A tarefa 1 faz esses testes passarem sem editá-los.

# Plano — Card e colunas (#8)

**Spec:** `docs/superpowers/specs/2026-10-15-card-e-colunas-design.md`
**Peso:** light · **Camada única:** core (agent `engineer`)

## Tarefas

### T1 — `Column` (core)
- Arquivo: `core/src/main/kotlin/sfv2/core/Column.kt`
- `enum class Column` com as 10 colunas na ordem do AC-2: INBOX, READY_FOR_REFINEMENT, REFINEMENT, SPEC_REVIEW, READY_FOR_DEV, CODING, VALIDATION, STAGED, READY_FOR_PROD, DONE.
- `displayName` com o nome exibido (ex.: "Ready for Refinement").
- Cobre: AC-2.
- Dependência: nenhuma.

### T2 — `Card` e validação (core)
- Arquivo: `core/src/main/kotlin/sfv2/core/Card.kt`
- `data class Card(val title: String, val column: Column)` com construtor primário `private`.
- `Card.create(title: String, column: Column? = null): Card`: apara o título; título vazio após o aparo lança `CardValidationException` (estende `RuntimeException`, mensagem cita o campo `title` e o valor não é logado); coluna ausente vira `Column.INBOX`.
- `CardValidationException` no mesmo arquivo ou em `core/src/main/kotlin/sfv2/core/CardValidationException.kt`.
- Cobre: AC-1, AC-3, AC-4.
- Dependência: T1.

## Ordem e arquivos
1. T1 → `Column.kt`
2. T2 → `Card.kt` (+ `CardValidationException`)

Testes: `core/src/test/kotlin/sfv2/core/CardTest.kt` (QA escreve antes da implementação).

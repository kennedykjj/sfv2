# Skill de validate via MCP — plano (#38)

**Peso:** standard · **Camada única:** `daemon` (agente `engineer`, feito pelo Tech Lead em modo solo)

## Tarefas

1. **Veredito** — `daemon/src/main/kotlin/sfv2/daemon/ValidationOutcome.kt`
   - `enum GatesState { GREEN, RED }`; `sealed interface ValidationDecision { Returned(layer, reason), Staged, Held, Malformed }`.
   - `class ValidationOutcome(store: CardEventStore, recorder: EventRecorder, layers: Set<String>, clock: Clock)` com `apply(cardId, report, gates)`.
   - Só a primeira linha decide: `VERDICT: APPROVED` ou `VERDICT: BLOCKED layer=<nome>`; camada fora de `layers` ou motivo acima de 2 KiB → `Malformed` (nada gravado).
   - `BLOCKED` → `MoveRules.check(VALIDATION, CODING, Factory("validate"))` e `CARD_MOVED` com `from`, `to`, `layer`, `reason`. `APPROVED` + `GREEN` → VALIDATION→STAGED; `APPROVED` + `RED` → `Held`.
   - Toda gravação por `EventRecorder.record`.

2. **Texto da skill** — `daemon/src/main/resources/skills/validate/SKILL.md`
   - pt-BR, só as ferramentas MCP existentes, três regras `<!-- rule:... -->` (untrusted-data e no-shell iguais às do refine/develop; write-scope: o validate não escreve código), formato do veredito no `completion_report`, `APPROVED` nunca basta sem gates verdes, não move card nem roda gate.
   - Carregada por `StageSkills.load("validate")`.

3. **Testes** (QA, antes): `ValidateSkillTest.kt`, `ValidationOutcomeTest.kt`.

## Ordem

Testes (QA) → 1 → 2.

## Arquivos

- Novos: `ValidationOutcome.kt`, `validate/SKILL.md`. Migração: nenhuma.

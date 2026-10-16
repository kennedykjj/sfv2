# Regras de movimento entre colunas — plano (#10)

**Spec:** `docs/superpowers/specs/2026-10-15-regras-de-movimento-entre-colunas-design.md` · **Peso:** standard

## Tarefas

### T1 — core: tabela e consulta de movimentos (camada `core`, agente `engineer`)

Dependências: nenhuma. Única camada, sem UI, sem migração.

Arquivos:
- `core/src/main/kotlin/sfv2/core/MoveRules.kt`: `enum MoveAuthority { OWNER_ONLY, FACTORY_OR_OWNER }`, `sealed interface MoveDecision { Allowed, Refused(reason) }` e `object MoveRules { table, check(from, to, actor) }` conforme a seção Architecture da spec.
- `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt`: escrito pela QA (passo 3), não por este plano.

Critérios cobertos: AC-1 a AC-6.

Observações:
- `check` é pura: sem IO, sem relógio; par ausente da tabela é `Refused`.
- Tabela única em `MoveRules.table`; nenhum outro lugar repete a regra.

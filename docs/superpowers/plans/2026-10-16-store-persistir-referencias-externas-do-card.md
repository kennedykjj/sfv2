# Plano: store — persistir referências externas do card (#111)

Spec: `docs/superpowers/specs/2026-10-16-store-persistir-referencias-externas-do-card-design.md`

## Tarefas

### T1 — layer `store`, agente `engineer` (única, sem dependências)
Arquivos:
- `store/src/main/sqldelight/migrations/3.sqm` (novo): cria `card_reference` (aditiva, sem tocar dados).
- `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: tabela `card_reference(card_id REFERENCES card(id), position, provider, external_id, url, imported_at, text_hash)` com `PRIMARY KEY (card_id, provider, external_id)`; queries de apagar, inserir e listar por card.
- `store/src/main/sqldelight/databases/3.db`: snapshot gerado pelo SQLDelight.
- `store/src/main/kotlin/sfv2/store/Database.kt`: `SCHEMA_VERSION = 4`.
- `store/src/main/kotlin/sfv2/store/CardEventStore.kt`: `recordChange` apaga e reinsere as referências na mesma transação; `card` e `cards` reaplicam `withReference` na ordem de `position`.

Testes de aceitação (QA, antes): `store/src/test/kotlin/sfv2/store/ModuleTest.kt` (AC-1 a AC-5); os testes que afirmam versão 3 passam a afirmar 4.

## Ordem
QA (testes vermelhos) → T1 → gates `scope`, `acceptance-intact`, `migration`, `build`, `green store`.

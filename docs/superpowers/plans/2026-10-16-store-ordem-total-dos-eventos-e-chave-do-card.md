# Plano: store — ordem total dos eventos e chave do card (#110)

Spec: `docs/superpowers/specs/2026-10-15-store-ordem-total-dos-eventos-e-chave-do-card-design.md`

## Tarefas

### T1 — layer `store`, agente `engineer` (única, sem dependências)
Arquivos:
- `store/src/main/sqldelight/migrations/2.sqm` (novo): recria `event` com `seq INTEGER PRIMARY KEY AUTOINCREMENT`, `id TEXT NOT NULL UNIQUE`, `card_id TEXT NOT NULL REFERENCES card(id)`; copia por ordem de `rowid`; recria índice `event_card_id`; descarta a tabela antiga.
- `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: `event` no formato final; `eventPayloadsByCard` com `ORDER BY seq`; `upsertCard` com `ON CONFLICT(id) DO UPDATE`.
- `store/src/main/kotlin/sfv2/store/Database.kt`: `SCHEMA_VERSION = 3`; driver com `foreign_keys=ON`.
- `store/build.gradle.kts`: `verifyMigrations.set(true)` (com `deriveSchemaFromMigrations` se exigido).

Testes de aceitação (QA, antes): `store/src/test/kotlin/sfv2/store/ModuleTest.kt` (AC-1 a AC-5).

## Ordem
QA (testes vermelhos) → T1 → gates `scope`, `acceptance-intact`, `migration`, `build`, `green store`.

# Store: ordem total dos eventos e chave do card — spec

**Issue:** #110 · **Branch:** `feat/110-store-ordem-total-dos-eventos-e-chave-do-card` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** a tabela `event` ordena só por `at` e não referencia `card`. Eventos no mesmo instante ficam sem ordem definida, e o histórico íntegro (trilha de auditoria, stream do #17) exige ordem total e integridade referencial.

**Audiência:** o desenvolvedor dentro de uma empresa (trilha de auditoria); indiretamente o daemon e o stream de eventos, que leem o histórico.

**Critérios de aceitação**
- AC-1: Dado eventos gravados, quando o histórico é lido, então cada evento tem um número de sequência crescente atribuído pelo banco e a leitura vem ordenada por ele, inclusive para eventos com o mesmo `at`.
- AC-2: Dado um evento de card, quando gravado com `card_id` inexistente, então a gravação é rejeitada (chave estrangeira, `foreign_keys=ON`); com card existente, é aceita.
- AC-3: Dado um banco na versão 1 ou na versão 2 com cards, eventos e specs, quando o store abre, então migra para a versão nova e todos os dados continuam legíveis, sem perda.
- AC-4: Dado o build do módulo `store`, quando `verifyMigrations` do SQLDelight roda, então ele está ligado e passa.
- AC-5: Dado um card já gravado com eventos, quando ele muda e é gravado de novo, então a linha é atualizada no lugar (upsert) e os eventos continuam ligados a ele.

**Fora do escopo:** eventos sem card (da fábrica), definidos no card que os criar; mudanças fora do `store`.

**Aderência ao produto:** serve "toda ação é um evento com ator" e "a fábrica é dona do próprio estado", com histórico íntegro e ordenado. Não contradiz posicionamento nem o que está fora do escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `store`. Sem mudança de contrato público (`CardEventStore.recordChange/events/card`, `SpecStore`).

- `store/src/main/sqldelight/migrations/2.sqm` (novo, migra v2→v3): recria `event` (SQLite não adiciona FK nem troca PK com `ALTER`) com `seq INTEGER PRIMARY KEY AUTOINCREMENT`, `id TEXT NOT NULL UNIQUE`, `card_id TEXT NOT NULL REFERENCES card(id)`; copia as linhas em ordem de `rowid` (preserva a ordem atual como `seq`); recria o índice `event_card_id`; descarta a tabela antiga. Roda dentro da transação já aberta por `Database.open`.
- `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: `event` no formato final acima; `eventPayloadsByCard` passa a `ORDER BY seq`; `upsertCard` vira `INSERT INTO card ... ON CONFLICT(id) DO UPDATE SET title = excluded.title, column_name = excluded.column_name`.
- `store/src/main/kotlin/sfv2/store/Database.kt`: `SCHEMA_VERSION = 3`; o driver abre com `foreign_keys=ON` (`Properties` do `JdbcSqliteDriver`, por conexão). Como o PRAGMA não muda dentro de transação, a migração não depende dele: `INSERT ... SELECT` com FK ligada falha e reverte tudo se houver evento órfão (banco antigo não deve ter, pois `recordChange` sempre grava o card antes).
- `store/build.gradle.kts`: `verifyMigrations.set(true)` no bloco `databases`; se a verificação exigir esquema-base, derivar das migrações (`deriveSchemaFromMigrations`) sem editar `1.sqm`.
- `spec` mantém `INSERT OR REPLACE`: não tem filhos com FK, fora do escopo.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-5 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
Migração destrutiva (recria `event`). Antes de aplicar, o banco é copiado (`<db>.v2.bak`) por quem opera; a migração é transacional, então falha reverte sozinha. Reverter a versão: restaurar a cópia e voltar o binário anterior (`DatabaseSchemaTooNewException` impede o binário antigo de abrir o banco v3).

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Nova migração `2.sqm` em vez de editar `1.sqm`/`Schema.sq` antigo: bancos v1 e v2 existem e `verifyMigrations` protege o histórico (decisão #17).
- `seq` AUTOINCREMENT e `id` UNIQUE: a ordem total vem do banco; AUTOINCREMENT impede reuso de número após remoção.
- Evento órfão numa migração aborta a abertura em vez de ser descartado: não perder histórico em silêncio.
- AC-4 de `verifyMigrations` é verificado pelo build (`./gradlew :store:build`) e por um teste de `ModuleTest.kt` que abre bancos v1 e v2 com dados; não há teste de unidade para o plugin.
- Teste no `ModuleTest.kt` existente: é a suíte do módulo `store`, sem arquivo novo.
- Peso `heavy` (a issue esperava `standard`): a migração recria tabela, o que conta como destrutiva pela regra de peso; o dono pode baixar na revisão.
- Rollback preenchido por a migração recriar tabela (destrutiva).

## Open questions
- none

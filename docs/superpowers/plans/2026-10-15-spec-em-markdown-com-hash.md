# Spec em Markdown com hash — plano

**Issue:** #14 · **Peso:** standard · **Camada:** store

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | store | engineer (solo, Tech Lead) | testes de aceitação (QA) | `store/src/main/kotlin/sfv2/store/SpecStore.kt`, `store/src/main/sqldelight/sfv2/store/db/Schema.sq`, `store/src/main/sqldelight/migrations/1.sqm`, `store/src/main/kotlin/sfv2/store/Database.kt` |

## Detalhe

1. Tabela `spec(card_id PK, path, hash)` no `Schema.sq` e migração `1.sqm`; `SCHEMA_VERSION` 1 → 2.
2. `SpecStore.write`: arquivo temporário + move atômico, depois upsert no banco.
3. `SpecStore.read`: `Valid`, `Diverged` (alterado ou ausente) ou `NotRegistered`.
4. `cardId` inválido → `SpecStoreException`.

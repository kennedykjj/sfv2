# Banco SQLite e migrações — plano (#12)

**Spec:** `docs/superpowers/specs/2026-10-15-banco-sqlite-e-migracoes-design.md` · **Peso:** standard

## Tarefas

### T1 — store: banco SQLite versionado (camada `store`, agente `engineer`)

Dependências: nenhuma. Única camada, sem UI.

Arquivos:
- `gradle/libs.versions.toml`: `sqldelight = "2.4.1"`, biblioteca `sqldelight-sqlite-driver` (`app.cash.sqldelight:sqlite-driver`) e plugin `sqldelight` (`app.cash.sqldelight`), versões fixas.
- `store/build.gradle.kts`: plugin `alias(libs.plugins.sqldelight)`, bloco `sqldelight` com o banco `Sfv2Database` no pacote `sfv2.store.db`, `implementation(libs.sqldelight.sqlite.driver)`.
- `store/src/main/sqldelight/sfv2/store/db/1.sq` (ou `Schema.sq`): tabelas `card` e `event`, índice por `card_id`.
- `store/src/main/kotlin/sfv2/store/Database.kt`: `Database.open(path)`, `SCHEMA_VERSION = 1`, `AutoCloseable`; lê `user_version` antes de qualquer PRAGMA de escrita; WAL depois; versão maior lança `DatabaseSchemaTooNewException`.
- `store/src/main/kotlin/sfv2/store/DatabaseSchemaTooNewException.kt`.
- `store/src/test/kotlin/sfv2/store/ModuleTest.kt`: escrito pela QA (passo 3), não por este plano.

Critérios cobertos: AC-1 a AC-4.

Observações:
- `build-logic/` não muda.
- Nenhum `!!`; exceção cita versão encontrada, suportada e caminho.
- Falha de abertura não pode escrever no arquivo (checar versão numa conexão somente leitura de cabeçalho antes de abrir pelo driver com escrita).

# Banco SQLite e migrações — spec

**Issue:** #12 · **Branch:** `feat/12-banco-sqlite-e-migracoes` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o banco é a fonte da verdade do estado da fábrica; precisa nascer versionado para que o esquema evolua sem perder dados nem ser corrompido por um código mais antigo.
**Audiência:** o desenvolvedor sozinho com muitos projetos (e o que está dentro de uma empresa), que confia o estado e a trilha de auditoria ao banco local do daemon.

**Critérios de aceitação**
- AC-1: Dado um caminho sem banco, quando o store abre, então o arquivo é criado com o esquema da versão 1 e o modo WAL ligado.
- AC-2: Dado um banco já na versão atual, quando é reaberto, então o esquema e os dados permanecem idênticos e nenhuma migração roda.
- AC-3: Dado um banco de versão mais nova que a do código, quando o store abre, então falha com mensagem clara que cita a versão encontrada e a suportada.
- AC-4: Dado o caso do AC-3, quando a abertura falha, então o arquivo do banco fica byte a byte inalterado.

**Fora do escopo:** backup; migrações além da versão 1; downgrade de esquema; tabelas de domínio além do necessário para a versão 1.

**Aderência ao produto:** serve "a fábrica é dona do próprio estado" e "toda ação é um evento": o banco local é a base do log de eventos. Não contradiz posicionamento nem escopo excluído.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos (módulo `store`, pacote `sfv2.store`):**
- `Database.open(path: Path): Database` — abre ou cria o banco; `Database : AutoCloseable`. Nenhum contrato fora do `store`.
- `DatabaseSchemaTooNewException(found: Int, supported: Int, path)` estende `RuntimeException`; mensagem cita a versão encontrada, a suportada e o caminho do arquivo.
- `Database.SCHEMA_VERSION = 1` (constante pública, lida pelos testes).

**Camadas e módulos:** só `store`. Não depende de `ports`/`adapters`; usa `core` apenas quando #13 mapear Card/Event.

**Esquema versão 1 (SQLDelight, `store/src/main/sqldelight/sfv2/store/`):** tabelas `card` (id, title, column_name) e `event` (id, card_id, at, actor, type, payload_json), com índice por `card_id`. O mapeamento de `core` para as linhas e a transação conjunta ficam no #13.

**Abertura:**
1. Arquivo inexistente: cria, grava o esquema v1 via `Schema.create`, define `PRAGMA user_version = 1` e liga `PRAGMA journal_mode=WAL`.
2. Versão igual à atual: só liga WAL (idempotente); nenhuma migração roda e os dados ficam intactos.
3. Versão maior que `SCHEMA_VERSION`: lê `user_version` numa conexão antes de qualquer `PRAGMA` de escrita (WAL altera o arquivo), lança `DatabaseSchemaTooNewException` e fecha sem escrever. Arquivo fica byte a byte igual.
4. Versão menor (futuro): `Schema.migrate` do SQLDelight com arquivos `.sqm`; migração aplicada nunca é editada. Sem migração na v1.

**Dependências (`gradle/libs.versions.toml`, versões fixas):** plugin `app.cash.sqldelight` 2.4.1 e `app.cash.sqldelight:sqlite-driver` 2.4.1 (a 2.1.0 é de 2025 e anterior ao Kotlin 2.4 do projeto) (traz o driver JDBC do SQLite). Aplicados em `store/build.gradle.kts`; `build-logic/` não muda.

**Migração:** expand-only, não destrutiva (banco novo). Sem `## Rollback` além de n/a.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Controle de versão pelo `PRAGMA user_version` do SQLDelight (`Schema.version`), sem tabela própria: é o mecanismo nativo do driver e não exige DDL extra.
- Versão do banco é checada antes de ligar WAL: o PRAGMA de WAL escreve no cabeçalho do arquivo e violaria o AC-3/AC-4.
- Esquema v1 inclui `card` e `event` porque o #13 depende deles; colunas mínimas, sem outras tabelas (fora do escopo).
- Testes entram em `ModuleTest.kt` (suíte existente do módulo); a classe pode ser renomeada pelo QA para `DatabaseTest.kt` só se mantiver o caminho do plano atualizado. Mantido o arquivo existente por regra de não criar suíte nova.
- Testes usam banco em diretório temporário (`@TempDir`), sem IO fora dele nem relógio real.
- SQLDelight como biblioteca de acesso ao SQLite do projeto (decisão #17 em docs/context/decisions.md): consultas tipadas e `verifyMigrations` para pegar migração editada; revisado pelo dono interino.
- Peso `standard`: uma camada, 4 ACs, 3 a 4 arquivos, sem segurança.

## Open questions
- none

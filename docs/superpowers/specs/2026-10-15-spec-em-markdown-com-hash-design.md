# Spec em Markdown com hash — spec

**Issue:** #14 · **Branch:** `feat/14-spec-em-markdown-com-hash` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** A spec do card precisa ser um arquivo legível e comparável em diff, mas o banco é o árbitro: sem um registro de caminho e hash, uma edição por fora passaria despercebida como se fosse a spec válida.
**Audience:** o desenvolvedor sozinho com muitos projetos (e, na empresa, quem precisa de trilha de auditoria): dono da fábrica, que aprova a spec e precisa saber que a aprovada é a que está no arquivo.
**Acceptance criteria**
- **AC-1** — Dado uma spec gravada para um card, quando a gravação termina, então o arquivo existe no caminho do card e o banco guarda esse caminho e o hash do texto.
- **AC-2** — Dado o arquivo da spec alterado ou removido fora do store, quando a spec é lida, então o store indica divergência em vez de devolver o texto como válido.
- **AC-3** — Dado um card sem spec gravada, quando a spec é lida, então o store indica que não há spec registrada, sem erro.
**Out of scope:** edição de spec pelo app; resolução da divergência (aceitar o arquivo ou restaurar); versões anteriores da spec.
**Product fit:** serve "a fábrica é dona do próprio estado" (o banco é o árbitro, o arquivo é legível) e a aprovação do dono sobre uma spec verificável; decisão #3 e S1-07 da fase 1. Não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `store`. Depende só de `core` (`TextHash`).

- **Contrato novo** `SpecStore(database: Database, specsDir: Path)` em `store/src/main/kotlin/sfv2/store/SpecStore.kt`:
  - `write(cardId: String, text: String)`: grava `<specsDir>/<cardId>.md` (UTF-8, escrita em arquivo temporário + move atômico) e faz upsert da linha em `spec` com `path` e `hash` (`TextHash.of(text)`), nessa ordem: o banco só aponta para arquivo já completo.
  - `read(cardId: String): SpecRead`, sealed: `Valid(text, hash)`, `Diverged(path, expected: TextHash, actual: TextHash?)` (`actual` nulo = arquivo ausente), `NotRegistered(cardId)`.
  - `cardId` com separador de caminho ou `..` → `SpecStoreException` (RuntimeException) com o id na mensagem; `specsDir` é criado se não existir.
- **Banco**: tabela `spec(card_id TEXT PK, path TEXT NOT NULL, hash TEXT NOT NULL)` em `Schema.sq` (estado final) e migração `store/src/main/sqldelight/migrations/1.sqm` (expand only, `CREATE TABLE`). `Database.SCHEMA_VERSION` 1 → 2.
- **Arquivos**: `SpecStore.kt` (novo), `Schema.sq`, `migrations/1.sqm` (novo), `Database.kt` (versão), `ModuleTest.kt` (testes + ajuste do AC-1 existente, que afirma versão 1).
- Sem migração destrutiva: bancos na versão 1 recebem só a tabela nova.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Caminho do card = `<specsDir>/<cardId>.md`: estável, único por card e sem depender do título (que muda).
- Hash SHA-256 reaproveitando `core.TextHash`: já existe e evita segunda implementação.
- Migração `1.sqm` e versão 2 em vez de editar o schema 1: bancos já criados não perdem a tabela, e `verifyMigrations` (decisão #17) protege o histórico.
- Testes no `ModuleTest.kt` existente: é a suíte do módulo; sem arquivo novo.
- Divergência cobre arquivo alterado e arquivo ausente: ambos significam que o texto não é o registrado.
- Leitura de spec não registrada devolve `NotRegistered`, não exceção: ausência é estado esperado de um card novo.

## Open questions
- none

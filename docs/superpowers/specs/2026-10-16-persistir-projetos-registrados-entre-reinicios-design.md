# Persistir os projetos registrados entre reinícios do daemon — spec

**Issue:** #210 · **Branch:** `feat/210-persistir-projetos-registrados-entre-reinicios` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o registro de projetos vive só em memória; qualquer reinício do daemon (queda, reboot, `service install`) o esvazia, e a fábrica fica muda em silêncio até alguém repetir `project add`.

**Público:** o desenvolvedor sozinho com muitos projetos (fábrica como serviço, sempre ligada, até dez projetos).

**Critérios de aceitação**
- AC-1: Dado um projeto registrado com `project add`, quando o daemon reinicia, então `project list` o mostra e o dispatcher o enxerga sem novo `project add`.
- AC-2: Dado o registro persistido, quando o `factory.yml` da `main` do projeto muda e o daemon sobe, então ele usa a configuração nova (só o caminho do repositório é persistido, nunca cópia da política).
- AC-3: Dado um caminho persistido que não existe mais ou com `factory.yml` inválido, quando o daemon sobe, então ele sobe, registra o motivo por projeto (só caminho e campo) e os demais projetos seguem registrados.
- AC-4: Dado um banco existente, quando a migração roda, então cards e eventos existentes seguem intactos (migração aditiva).
- AC-5: Dado o mesmo caminho registrado duas vezes, quando o daemon reinicia, então aparece uma única vez.

**Fora de escopo**
- Persistir cópia da política ou do `factory.yml`.
- Recuperar ou reparar automaticamente projetos com caminho inválido (ficam persistidos, só registram o motivo).
- Mudança de UI, de CLI ou do protocolo.

**Aderência ao produto:** serve "a fábrica é dona do próprio estado" e "sempre ligada, também com o app fechado". Não contradiz posicionamento nem o fora de escopo. Bloqueia a virada (#62).

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Schema (store):** migração `store/src/main/sqldelight/migrations/6.sqm` cria `project(repo_path TEXT PRIMARY KEY, registered_at TEXT NOT NULL)`; `Schema.sq` ganha a mesma tabela e as queries `insertProject` (`INSERT OR IGNORE`) e `allProjects` (ordem de `rowid`). `Database.SCHEMA_VERSION` passa de 6 para 7. Só caminho; nunca política.
- **API (store):** novo `ProjectPathStore(database)` em `store/src/main/kotlin/sfv2/store/ProjectPathStore.kt` com `add(path: String, at: Instant)` e `all(): List<String>`.
- **Daemon:** `ProjectRegistry` recebe `ProjectPathStore` e `DaemonLog`; `register` grava o caminho canônico depois de validar o `factory.yml` (registro recusado não persiste). Novo `restore()` lê os caminhos persistidos, registra cada um em memória sem regravar e, por projeto, captura `ProjectNotFoundException`/`InvalidProjectConfigException` logando `event=project_restore_failed path=<caminho> field=<campo>` (só caminho e campo); o caminho inválido permanece persistido. `Main.kt` chama `registry.restore()` logo após criar o registro, antes de `RefineRuntime.start`.
- **Contratos:** nenhum endpoint, evento ou protocolo muda; `project.add`/`project.list` iguais. Não há `project remove` hoje (AC de remoção do PO retirado).
- **Camadas:** `store`, `daemon`. Testes existentes que afirmam `SCHEMA_VERSION == 6` / `user_version = "6"` em `ModuleTest.kt` passam a 7.
- **Migração:** expand-only (tabela nova), sem passo destrutivo.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- AC de remoção do PO retirado: não existe `project remove`; o AC de duplicata virou AC-5.
- Persiste só o caminho canônico, após validar o `factory.yml`: um registro recusado não deixa rastro.
- Caminho que falha na subida continua persistido e é só logado: o dono pode consertar o repositório sem repetir `project add`.
- `restore()` roda no `Main` antes do dispatcher, para ele já enxergar os projetos.
- Sem arquivo de teste novo: `ProjectRegistryTest.kt` e `ModuleTest.kt` cobrem os módulos.
- Peso standard: duas camadas, 5 ACs, 5 arquivos.

## Open questions
- none

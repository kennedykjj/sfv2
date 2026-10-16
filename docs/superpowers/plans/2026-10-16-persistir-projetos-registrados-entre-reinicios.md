# Plano — Persistir os projetos registrados entre reinícios do daemon (#210)

Spec: `docs/superpowers/specs/2026-10-16-persistir-projetos-registrados-entre-reinicios-design.md`

## Tarefas

1. **store** (agente `engineer`, `store/`)
   - `store/src/main/sqldelight/migrations/6.sqm`: cria `project(repo_path TEXT PRIMARY KEY, registered_at TEXT NOT NULL)`.
   - `Schema.sq`: mesma tabela; queries `insertProject` (`INSERT OR IGNORE`) e `allProjects` (ordem de `rowid`).
   - `Database.SCHEMA_VERSION` 6 → 7.
   - `ProjectPathStore(database)` em `store/src/main/kotlin/sfv2/store/ProjectPathStore.kt`: `add(path: String, at: Instant)` e `all(): List<String>`.
   - Dependências: nenhuma.

2. **daemon** (agente `engineer`, `daemon/`; depende da tarefa 1)
   - `ProjectRegistry(source, paths: ProjectPathStore? = null, log: DaemonLog = StderrDaemonLog, clock: Clock = Clock.systemUTC())`: `register` grava o caminho canônico após validar o `factory.yml`; novo `restore()` registra em memória os caminhos persistidos sem regravar e, por projeto, loga `event=project_restore_failed path=<caminho> field=<campo>` para `ProjectNotFoundException`/`InvalidProjectConfigException`.
   - `Main.kt`: cria o `ProjectPathStore(database)`, passa ao registro e chama `registry.restore()` antes de `RefineRuntime.start`.

## Testes
- `ModuleTest.kt` (store): `SCHEMA_VERSION`/`user_version` passam a 7; migração aditiva (AC-4).
- `ProjectRegistryTest.kt` (daemon): AC-1, AC-2, AC-3, AC-5.

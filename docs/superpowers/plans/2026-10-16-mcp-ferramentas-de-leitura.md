# MCP: ferramentas de leitura — plano (#28)

Spec: `docs/superpowers/specs/2026-10-16-mcp-ferramentas-de-leitura-design.md`

## Tarefas

1. **store** (agente `engineer`) — vínculo card→projeto.
   - Migração `store/src/main/sqldelight/migrations/4.sqm`: tabela `card_project(card_id TEXT PRIMARY KEY, repo TEXT NOT NULL)`; consultas em `Schema.sq`; snapshot `3.db` gerado pela tarefa do SQLDelight (`generateSchema`), como os anteriores.
   - `CardEventStore.assignProject(cardId, repo)` e `projectOf(cardId): String?`, fora da transação de `recordChange`.
   - Arquivos: `store/src/main/**`, sem tocar em `store/src/test/**` do QA além do teste de migração v3→v4 no `ModuleTest.kt` (feito pelo QA).
   - Dependências: nenhuma.

2. **daemon** (agente `engineer`) — ferramentas de leitura e transporte. Depende da 1.
   - `gradle/libs.versions.toml` + `daemon/build.gradle.kts`: SDK oficial de MCP em Kotlin e transporte HTTP, versão fixa já definidos no catálogo (`libs.mcp.kotlin.sdk.server` 0.15.0 e `libs.ktor.server.cio` 3.5.1): **não procure o SDK no cache do Gradle** (fora das pastas permitidas); a API necessária está resumida em `docs/superpowers/notes/2026-10-16-mcp-kotlin-sdk-0.15.0.md`. Leia a nota primeiro. O `McpCardView` do engenheiro está certo (`CardView` já existe em `Protocol.kt`); renomear o stub equivalente do QA para o mesmo nome, se ele conflitar.
   - `UntrustedText` (envelope `{trust, source, text}`), `ProjectFileSource` (`GitProjectConfigSource` passa a implementá-la, limite/timeout compartilhados; limite de 256 KiB para arquivo e spec).
   - `McpSession`/`McpSessions.open(repo): token` (SecureRandom 32 bytes, comparação em tempo constante).
   - `McpReadTools`: `card_get`, `spec_get`, `review_criteria_get`, `policies_get`, `layers_get`; recusa por projeto antes de ler; log `key=value` só com ids.
   - `McpServerFactory`: registra as ferramentas no SDK; loopback, `Authorization: Bearer`, checa `Host` e recusa `Origin`.
   - Arquivos: `daemon/src/main/**`, `gradle/libs.versions.toml`, `daemon/build.gradle.kts`.

## Ordem
QA escreve `McpReadToolsTest.kt` (daemon) e o teste de migração (store) antes; depois tarefa 1, depois tarefa 2.

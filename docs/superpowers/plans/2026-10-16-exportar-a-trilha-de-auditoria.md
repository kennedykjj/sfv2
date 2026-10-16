# Plano — Exportar a trilha de auditoria (#89)

Spec: `docs/superpowers/specs/2026-10-16-exportar-a-trilha-de-auditoria-design.md`

## Tarefas

1. **daemon** (engineer) — sem dependências.
   - `daemon/src/main/kotlin/sfv2/daemon/AuditExport.kt` (novo): `AuditExport.render(cardId, events, format)` puro, colunas fechadas, neutralização de fórmulas igual à do `CardReport`.
   - `Protocol.kt`: `Request.AuditExport`, parse de `audit.export`, `Response.AuditResult`, render.
   - `CardCommands.kt`: handler read-only (id desconhecido ou formato inválido → `Response.Error` com o id).
   - `SocketServer.kt`: roteamento sem `proof`.
2. **cli** (engineer) — depende da tarefa 1.
   - `cli/src/main/kotlin/sfv2/cli/AuditCommands.kt` (novo): grupo `audit`, comando `export <id> --format json|csv` (default `json`).
   - `CardCommands.kt`: registro em `withCardCommands`.

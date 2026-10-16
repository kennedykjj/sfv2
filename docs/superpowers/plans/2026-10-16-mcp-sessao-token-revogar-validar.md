# MCP: amarrar sessão ao token, revogar tokens e validar entradas — plano

**Issue:** #142 · **Spec:** `docs/superpowers/specs/2026-10-16-mcp-sessao-token-revogar-validar-design.md`

Todas as tarefas na camada `daemon` (agente: engineer), em sequência.

## Tarefas

1. **McpSessions** (`daemon/src/main/kotlin/sfv2/daemon/McpSession.kt`): `Clock` e `ttl` injetados (`require(ttl > 0)`), `expiresAt` fixo na abertura, `find` descarta token expirado, `close(token)`, `bind(httpSessionId, token)`, `owns(httpSessionId, token)` com digest e `MessageDigest.isEqual`. `McpRequestGuard.authorize` recebe `sessionId: String?`. Dependências: nenhuma.
2. **McpServerFactory** (`McpServerFactory.kt`): filtro lê `Mcp-Session-Id`, liga id→token ao `initialize`, recusa id de outro token/desconhecido/encerrado; `close`/expiração encerram sessões HTTP; `toolResult` converte `RuntimeException` não de domínio em `tool failed (code=internal)` e loga `mcp_tool_failed tool= code= error=<classe>`. Depende de 1.
3. **McpReadTools** (`McpReadTools.kt`): `cardId` validado por `Regex("[A-Za-z0-9-]{1,64}").matches` antes de qualquer consulta; recusa `argument cardId is invalid`, log `reason=invalid_card_id` sem o valor; revisar `McpToolException` para não interpolar texto do agente. Depende de 2.

## Testes
Existentes: `McpServerFactoryTest.kt` (AC-1, AC-2, AC-4), `McpReadToolsTest.kt` (AC-3).

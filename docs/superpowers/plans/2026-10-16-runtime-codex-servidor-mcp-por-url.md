# Plano: runtime codex com servidor MCP por URL (#135)

Spec: `docs/superpowers/specs/2026-10-16-runtime-codex-servidor-mcp-por-url-design.md`

## Tarefas

### T1 — `adapters`: `env` no `ProcessRunner` (agente: engineer)
- `ProcessRunner.run(..., env: Map<String, String> = emptyMap())` em `CodexRuntime.kt`.
- `ProcessRunnerImpl.kt` soma `env` ao ambiente herdado do `ProcessBuilder`; nunca imprime `env` em exceções.
- Arquivos: `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt`, `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt`.

### T2 — `adapters`: servidor por URL e erros sem conteúdo (agente: engineer, depende de T1)
- `McpServerEntry` aceita `command`/`args` ou `url` + `headers`.
- Servidor por URL: `-c mcp_servers.<nome>.url=...` e `-c mcp_servers.<nome>.bearer_token_env_var="SFV2_MCP_TOKEN_<NOME>"`; token (de `headers.Authorization`, `Bearer <token>`) só em `env`.
- Validações: nome do servidor, colisão de variável, `url` com token só `http(s)` loopback.
- Erros de leitura/decodificação citam só caminho e campo, sem `e.message` nem `cause`.
- Arquivo: `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt`.

## Testes
QA escreve em `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` (fake do `ProcessRunner` registra `env`).

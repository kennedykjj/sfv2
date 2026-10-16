# Runtime via Bedrock, Vertex e proxy — plano (#87)

Spec: `docs/superpowers/specs/2026-10-16-runtime-via-bedrock-vertex-e-proxy-design.md`

## Tarefas

### T1 — layer `adapters` (agente: engineer)
Depende de: nada.
- Criar `adapters/src/main/kotlin/sfv2/adapters/RuntimeProvider.kt`: `sealed interface RuntimeProvider` (`Anthropic`, `Bedrock`, `Vertex`, `Proxy`), `fromEnv(env)`, `environment()`, `InvalidRuntimeProviderException(field)`; validação conforme a spec (URL http/https com host, sem credenciais, query nem fragmento; mensagens nomeiam o campo, nunca o valor).
- `ClaudeRuntime.kt`: parâmetro `provider`; limpar do ambiente do subprocesso as 9 variáveis da spec e aplicar `provider.environment()` antes de iniciar.
- Os testes de aceitação (`ClaudeRuntimeTest.kt`) vêm do QA.

### T2 — layer `daemon` (agente: engineer)
Depende de: T1.
- `daemon/src/main/kotlin/sfv2/daemon/RefineRuntime.kt`: `RuntimeProvider.fromEnv(env)` no início da montagem (antes de subir o endpoint MCP) e passar ao `ClaudeRuntime`; configuração inválida falha a subida nomeando o campo.
- AC-6: sem mudança de código esperada; `RunCostTest.kt` vem do QA.

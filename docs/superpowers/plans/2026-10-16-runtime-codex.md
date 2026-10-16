# Runtime codex — plano

**Issue:** #23 · **Spec:** `docs/superpowers/specs/2026-10-16-runtime-codex-design.md`

## Tarefas

| # | Camada | Agente | Dependência | Arquivos |
|---|---|---|---|---|
| 1 | adapters | engineer (solo) | testes de aceitação (QA) | `adapters/build.gradle.kts`, `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt`, `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt` |

## Tarefa 1 — CodexRuntime

- `ProcessRunner`, `ProcessOutcome` (`Exited(code)` | `TimedOut`) e `CodexRuntime` implementando `sfv2.ports.Runtime`.
- Valida o stage (`[a-z][a-z0-9-]*`) e os nomes de servidor MCP (`[A-Za-z0-9_-]+`) antes de qualquer efeito; erro nomeia o campo.
- Lê o JSON do `mcpConfig` (kotlinx-serialization-json, já no catálogo) e monta `-c mcp_servers.<nome>.command=<TOML string>` e `.args=[...]`.
- Transcrição em `<transcriptDir>/<stage>-<epochMillis>.jsonl`; código ≠ 0 → `Exited(code, path)`; timeout → `Hung(path)`.
- `ProcessRunnerImpl` com `ProcessBuilder`, redirect do stdout para arquivo, timeout e kill da árvore.
- `adapters/build.gradle.kts`: plugin de serialização e dependência do JSON.

## Testes

`adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` (QA), com `ProcessRunner` fake.

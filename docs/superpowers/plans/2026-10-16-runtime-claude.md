# Runtime claude — plano (#22)

Spec: `docs/superpowers/specs/2026-10-16-runtime-claude-design.md`

## Tarefas

### T1 — layer `adapters` (agente: engineer; execução solo pelo Tech Lead)
Depende de: nada (a port `Runtime` já existe).

- Criar `ClaudeRuntime(binary = "claude", transcriptDir: Path, timeout: Duration, clock: Clock = Clock.systemUTC()) : Runtime` e `RuntimeBinaryNotFoundException(binary, stage)` no mesmo arquivo.
- Validar `stage` (`[a-z][a-z0-9-]*`) e `worktree` existente antes de iniciar (AC-8).
- Montar o comando `claude -p --permission-mode acceptEdits --strict-mcp-config --mcp-config <mcpConfig> --model <model> --output-format stream-json --verbose`, diretório = worktree, prompt `/sf:<stage>` pelo stdin.
- stdout+stderr em `transcriptDir/<stage>-<instante>.jsonl`.
- Espera com timeout; estouro mata o processo e descendentes e devolve `Hung`.
- Binário ausente → `RuntimeBinaryNotFoundException`.

Arquivos:
- `adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt` (novo)
- `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` (novo, QA)

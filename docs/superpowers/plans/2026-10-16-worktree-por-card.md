# Worktree por card — plano (#24)

Camada única: `daemon` (peso standard). Agente dono: `engineer` (feito direto pelo Tech Lead, modo solo).

## Tarefas

1. **daemon** — `WorktreeGit.kt`: interface `WorktreeGit` e `SystemWorktreeGit` (`git -C` via `ProcessBuilder`, timeout, `GIT_TERMINAL_PROMPT=0`, `-c core.fsmonitor=false`).
2. **daemon** — `WorktreeManager.kt`: `CardWorktree`, `CleanupResult`, `WorktreeException`, `WorktreeManager` (`ensure`/`cleanup`, validação de segmentos, branch por `branchFormat`).

Dependência: 2 usa 1. Arquivos: `daemon/src/main/kotlin/sfv2/daemon/WorktreeGit.kt`, `WorktreeManager.kt`.

Testes de aceitação (QA): `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt`, com fake de `WorktreeGit`.

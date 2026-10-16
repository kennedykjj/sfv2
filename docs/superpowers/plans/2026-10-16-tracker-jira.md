# Tracker Jira — plano

**Issue:** #85 · **Spec:** `docs/superpowers/specs/2026-10-16-tracker-jira-design.md`

Tudo na camada `adapters` (agente `engineer`). Tarefas em sequência, no mesmo worktree.

## Tarefa 1 — ambiente filtrado no ProcessRunner
- Camada: adapters · Agente: engineer
- Arquivos: `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt` (interface `ProcessRunner`), `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt`.
- Novo modo de ambiente filtrado, retrocompatível com os chamadores atuais (padrão = comportamento de hoje): o filho recebe só `PATH`, `HOME`, `LANG`, `LC_*` e as variáveis do próprio `acli`; nomes com `TOKEN`, `SECRET`, `PASSWORD` ou `KEY` ficam de fora. O modo é um enum, não booleano.

## Tarefa 2 — AcliTracker
- Camada: adapters · Agente: engineer · Depende da tarefa 1
- Arquivo: `adapters/src/main/kotlin/sfv2/adapters/AcliTracker.kt` (novo), espelhando `GhTracker.kt`.
- `fetch`, `search`, `writeMilestone`, `close` via `acli jira workitem view/search/comment/transition` com saída JSON; validação de chave e site sem spawn; JQL fixa com consulta entre aspas escapadas; ADF vira texto simples; `close` idempotente (`AlreadyClosed`); limites de 8 MiB, timeout, truncamento falha; erros só com operação + id.
- Os testes de aceitação (`AcliTrackerTest.kt`) vêm do QA e não são editados.

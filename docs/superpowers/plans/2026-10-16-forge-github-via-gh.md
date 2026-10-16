# Forge GitHub via gh — plano

**Issue:** #44 · **Spec:** `docs/superpowers/specs/2026-10-16-forge-github-via-gh-design.md`

## Tarefas

### T1 — `GhForge` (camada `adapters`, agente `engineer`)
- Arquivo: `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt`.
- Implementa `sfv2.ports.Forge` sem alterá-la, rodando `gh` pelo `ProcessRunner` existente, com `GH_PROMPT_DISABLED=1`.
- Validação de entrada (`head`, `expectedHead`, `repo`, `branch`) antes de qualquer chamada ao `gh`.
- `openPullRequest` idempotente via `findOpenPullRequest`; corpo em arquivo de `tmpDir` apagado no `finally`.
- `merge` com checagem prévia (`NotOpen`, `HeadMismatch`, `Conflict`) e `--match-head-commit`.
- `checks` paginado: lista vazia ou check não concluído = `Pending`; só `success`, `neutral` e `skipped` passam.
- Falhas do `gh` viram `ForgeException` sem stderr bruto.

## Dependências
- Nenhuma entre tarefas; os testes de aceitação (QA, `GhForgeTest.kt`) vêm antes de T1.
- Sem dependência nova (`kotlinx.serialization.json` já é do módulo).

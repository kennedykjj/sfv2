# Plano — Tracker GitHub Issues via gh (#53)

Spec: `docs/superpowers/specs/2026-10-16-tracker-github-issues-via-gh-design.md`

## Tarefas

### T1 — `GhTracker` (layer `adapters`, agente: Tech Lead, solo)
- Dependências: nenhuma (porta `Tracker` já existe).
- Arquivos: `adapters/src/main/kotlin/sfv2/adapters/GhTracker.kt` (novo).
- Escopo: `fetch`, `search`, `writeMilestone`, `close` sobre `gh`, no formato do `GhForge`; validação de entrada (AC-7), teto de 8 MiB (AC-9), busca truncada em 100 (AC-8), textos como `ExternalText` (AC-4), erros sem texto da issue (AC-5).

## Testes de aceitação
- QA: `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` (novo, reusa `GhHarness`).

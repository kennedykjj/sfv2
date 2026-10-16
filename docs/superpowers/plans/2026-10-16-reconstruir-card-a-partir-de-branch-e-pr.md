# Reconstruir card a partir de branch e PR — plano

**Issue:** #51 · **Spec:** `docs/superpowers/specs/2026-10-16-reconstruir-card-a-partir-de-branch-e-pr-design.md`

Tarefas na ordem de dependência (cada camada depende da anterior). Todas com o agente `engineer`.

## T1 — ports
- `Forge.listPullRequests()`, `Forge.authenticatedLogin()`, `PullRequestSummary`.
- `FakeForge`: lista as entradas com estado, autor e `fromFork`; respeita `failNext("listPullRequests")`.
- Arquivos: `ports/src/main/kotlin/sfv2/ports/Forge.kt`, `ports/src/main/kotlin/sfv2/ports/FakeForge.kt`.

## T2 — adapters (depende de T1)
- `GhForge.listPullRequests` (`gh pr list --state all --limit 1000 --json ...`) e `authenticatedLogin` (`gh api user --jq .login`, validado por regex).
- Arquivo: `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt`.

## T3 — daemon (depende de T1)
- `CardRebuild.kt` (lógica), despacho em `CardCommands.kt`, parse de `card.rebuild` em `Protocol.kt`.
- Cobre AC-1 a AC-7, AC-9, AC-10 (`CardRebuildTest.kt`, escrito pelo QA).

## T4 — cli (depende de T3)
- `sfv2 card rebuild [--project <nome>]` em `cli/src/main/kotlin/sfv2/cli/CardCommands.kt`.

## Testes de aceitação (QA)
- `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` (AC-1..7, 9, 10).
- `e2e/src/test/kotlin/sfv2/e2e/PhaseThreeExitTest.kt` (AC-8).

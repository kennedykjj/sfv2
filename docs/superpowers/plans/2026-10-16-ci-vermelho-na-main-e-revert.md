# CI vermelho na main e revert — plano (#50)

Spec: `docs/superpowers/specs/2026-10-16-ci-vermelho-na-main-e-revert-design.md`. Peso heavy, quatro camadas, executadas nesta ordem (cada uma depende da anterior).

## Tarefas

1. **core** (agente `engineer`) — `core/src/main/kotlin/sfv2/core/Event.kt`
   - Novo `EventType.MAIN_CI_RERUN`. Aditivo; sem migração.
2. **ports** (agente `engineer`; depende de 1 só pelo catálogo) — `ports/src/main/kotlin/sfv2/ports/Forge.kt`, `FakeForge.kt`, `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt`
   - `Forge.rerunChecks(head: String)` e `Forge.revert(pr: PullRequestRef): PullRequestRef`.
   - `FakeForge`: `reruns` e `reverts`, `failNext("rerunChecks" | "revert")`, o revert abre um PR novo e o devolve; testes de fake em `ForgeTest`.
3. **adapters** (depende de 2) — `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt`, `GhForgeTest.kt`
   - `rerunChecks`: `gh api repos/<repo>/actions/runs?head_sha=<sha>` e `POST actions/runs/{id}/rerun-failed-jobs` para cada run concluído com falha; sha validado por `^[0-9a-f]{40}$`, ids como inteiros.
   - `revert`: `gh pr view --json id` e mutation GraphQL `revertPullRequest`; devolve o PR aberto.
   - `ForgeException` com operação e número do PR, nunca saída do `gh`.
4. **daemon** (depende de 2; critério de aceitação) — `daemon/src/main/kotlin/sfv2/daemon/AutonomousDelivery.kt`
   - `followMain`: primeiro `Failed` sem `MAIN_CI_RERUN` → `forge.rerunChecks`, grava o evento só depois do sucesso, volta a acompanhar ignorando `Failed` até ver um `Pending`.
   - Com `MAIN_CI_RERUN` gravado e `Failed` pós-`Pending` → `forge.revert` do PR do `PR_MERGED`; Staged com `main_ci_red ...` e `revert_pr=<n>`.
   - Sem `Pending` até esgotar as consultas → Staged `rerun_not_started`, sem revert.
   - `ForgeException` no rerun → `forge_failed`; só no revert → `revert_failed` mantendo o vermelho no motivo. Timeout continua `main_ci_timeout`.
   - Ajustar o teste existente "AC-5 a red main goes back to the owner...".

## Testes de aceitação

Todos em `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` (AC-1 a AC-6).

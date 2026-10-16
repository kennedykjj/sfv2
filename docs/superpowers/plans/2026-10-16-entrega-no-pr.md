# Entrega no PR — plano (#47)

Spec: `docs/superpowers/specs/2026-10-16-entrega-no-pr-design.md`. Peso heavy. A linha `(READY_FOR_PROD to STAGED)` de `MoveRules` e o gancho `DeliveryTrigger` no `Dispatcher` já estão na main (#46); só há uma camada de trabalho.

## Tarefa 1 — daemon (agente: engineer)
- `daemon/.../PullRequestDelivery.kt`: `CiResult.Green` passa a carregar o número do PR (`Green(pr: Int)` ou equivalente, conforme `PullRequestRef`); `deliver` o preenche com o PR que abriu.
- `daemon/.../ValidationOutcome.kt`: o payload do `CARD_MOVED` para Staged ganha `pr=<número>` junto de `ci=green` e `head`. Única mudança no validate; ajustar os testes existentes que constroem `CiResult.Green`.
- `daemon/.../PullRequestHandoff.kt` (novo): `approve(cardId): HandoffOutcome` (em modo PR, nunca chama `Forge.merge`; o card fica esperando em Ready for Prod) e `reconcile(cardId): HandoffOutcome` com `Waiting`, `Merged`, `ClosedUnmerged`, `ForgeFailed`. `reconcile` só age em card em Ready for Prod de projeto `PR_HANDOFF`; lê o `pr` do último `CARD_MOVED` para Staged do ator `Factory("validate")`; sem ele, move para Staged (ator fábrica) com `reason=no_pr_recorded`; `Forge.state` Merged → Done; Closed sem merge → Staged com `reason=pr_closed_unmerged`; Open ou `ForgeException` → fica (Waiting / ForgeFailed). Estado do forge é dado, nunca instrução.
- `daemon/.../AutonomousDelivery.kt` / `RefineRuntime.kt`: fiar o `reconcile` no mesmo `DeliveryTrigger` que o `Dispatcher` já chama a cada ciclo para cards em Ready for Prod (trigger composto ou o `AutonomousDeliveryTrigger` chamando os dois; cada passo se pula no modo que não é o seu).
- Testes de acompanhamento: `MoveRulesTest` (linha já coberta pelo #46; conferir), ajuste de `ValidatePipelineTest`/`ValidationOutcome`. Os de aceitação vêm do QA em `PullRequestHandoffTest.kt`.

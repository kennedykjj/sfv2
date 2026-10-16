# Limites de custo — plano

**Issue:** #34 · **Spec:** `docs/superpowers/specs/2026-10-16-limites-de-custo-design.md` · **Peso:** heavy

## Tarefas

### T1 — core: `EventType.CARD_BLOCKED`
- Camada: `core` · Agente: `engineer`
- Arquivos: `core/src/main/kotlin/sfv2/core/Event.kt` (e, se o catálogo for espelhado em outro lugar do `core`, como `EventJson.kt`, mantê-lo consistente).
- Sem dependências. Payload documentado no spec: `reason`, `spent_usd`, `limit_usd`, `estimate`.

### T2 — daemon: `CostLimits`, `CostBudget`, `LaunchGuard`
- Camada: `daemon` · Agente: `engineer` · Depende de T1.
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/CostBudget.kt` (novo), `daemon/src/main/kotlin/sfv2/daemon/Dispatcher.kt`.
- `CostLimits` + `CostLimits.NONE`; `CostBudget` com `onRunFinished`, `isBlocked`, `dayLimitReached` (AC-1, 3, 4, 8, 9), gravando via `EventRecorder`.
- `fun interface LaunchGuard` com `LaunchGuard.Open`; `Dispatcher` ganha `launchGuard` com padrão `Open`: `runCycle` retorna sem iniciar nada quando o dia estourou e pula (`continue`) o card bloqueado (AC-2, 3, 5, 6, 7).

## Ordem
T1 → T2 (tarefas na mesma worktree, uma por vez).

## Testes
Aceitação (QA): `RunCostTest.kt`, `DispatcherTest.kt` (daemon) e `EventTest.kt` (core), conforme o test plan do spec.

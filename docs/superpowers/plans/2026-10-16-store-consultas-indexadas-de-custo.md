# Store: consultas indexadas de custo do dia e bloqueio por card — plano

**Issue:** #151 · **Spec:** `docs/superpowers/specs/2026-10-16-store-consultas-indexadas-de-custo-design.md`

## Tarefas

### T1 — store (agente: engineer)
Depende de: nada.
- `Schema.sq`: índices `event_type_at ON event(type, at)` e `event_card_type_actor ON event(card_id, type, actor, seq)`; consultas `costEventsBetween` e `lastSeq`.
- `migrations/5.sqm`: os dois `CREATE INDEX` (aditivo).
- `Database.kt`: `SCHEMA_VERSION` 5 → 6.
- `CardEventStore.kt`: `costEventsBetween(from, toExclusive)` e `lastSeq(cardId, type, actor = null)`; limites de data como texto do `Instant` sem o `Z` final; só as linhas devolvidas passam por `EventJson.decode`.
- Ajustar testes existentes que afirmam `user_version` 5 para 6 (só se não forem os testes de aceitação do QA).

### T2 — daemon (agente: engineer)
Depende de: T1.
- `CostBudget.kt`: `dayLimitReached()` usa `costEventsBetween` com `[início do dia em zone, início do dia seguinte)` a partir do `Clock`; `isBlocked(cardId)` usa `lastSeq(card, CARD_BLOCKED)` e `lastSeq(card, CARD_MOVED, Actor.Owner)`. `onRunFinished` não muda.

## Testes
Aceitação (QA): `store/.../ModuleTest.kt` (AC-1..3) e `daemon/.../RunCostTest.kt` (AC-4, regressão do #34).

# Pool, slots e locks — plano (#25)

Spec: `docs/superpowers/specs/2026-10-16-pool-slots-e-locks-design.md`. Base: main com o #124.

## Interface acordada (para QA e engenheiro)

- `core`: `EventType.LOCK_RELEASED`; payload `reason` (`owner_dead`) e `pid`; ator `Factory("pool")`.
- `daemon`, pacote `sfv2.daemon`: `ProcessProbe` (`fun interface`, `isAlive(pid: Long, startedAt: Instant)`), `SystemProcessProbe`, `RunLock(cardId, pid, startedAt)`, `StartDecision` (`Started(lock)`, `PoolFull(capacity)`, `CardLocked(holder)`).
- `RunPool(capacity, store, clock, probe, log = StderrDaemonLog, onEvent = {})` com `tryStart(cardId, pid, startedAt)`, `release(cardId)`, `sweep(): List<RunLock>`, `active(): List<RunLock>`; métodos `@Synchronized`.
- `tryStart` checa o lock do card antes da capacidade; recusa não grava nada. `capacity` ≤ 0 lança `IllegalArgumentException` com o valor.
- `sweep` libera locks cujo `probe.isAlive` é falso, grava `LOCK_RELEASED` via `store.recordChange` (card inalterado), chama `onEvent` com o `StoredEvent` e loga `lock_released card=<id> pid=<pid>`. Lock vivo: nada muda, nenhum evento. Card inexistente no store: libera, loga `lock_release_orphan_card`, sem evento.
- Testes usam um fake de `ProcessProbe` e `Clock` fixo; sem `Thread.sleep`.

## Tarefas

### 1. core (engineer) — `core/`
- `Event.kt`: acrescentar `LOCK_RELEASED` ao `EventType`.
- Estender o teste de serialização existente (`EventTest`) para cobrir o novo tipo, sem AC próprio.

### 2. daemon (engineer) — `daemon/`, depende da 1
- Novo `ProcessProbe.kt`: interface e `SystemProcessProbe` via `ProcessHandle` (vivo só se o PID existe e o início confere).
- Novo `RunPool.kt`: tipos e pool conforme a interface acima.
- Logs só com ids e pid.

## Dono dos arquivos
`core/` e `daemon/` (agente `engineer`); testes de aceitação em `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` (QA).

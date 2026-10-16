# Pool, slots e locks — spec

**Issue:** #25 · **Branch:** `feat/25-pool-slots-e-locks` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Vários projetos disputam a mesma máquina; sem um limite de runs simultâneos e sem lock por card, o dispatcher pode sobrecarregar a máquina ou pôr dois runs no mesmo card, e um run morto deixaria o card preso (G15 da V0).
**Audience:** O desenvolvedor sozinho com muitos projetos, que roda vários cards em paralelo na mesma máquina.
**Acceptance criteria**
- **AC-1** — Given o pool com todos os slots ocupados, when um novo run tenta começar, then ele é recusado e nenhum lock novo é criado.
- **AC-2** — Given um card com lock, when outro run tenta pegar o mesmo card, then ele é recusado e o lock original continua com o dono original.
- **AC-3** — Given um lock cujo processo dono morreu, when o próximo ciclo roda, then o lock é liberado, o slot volta ao pool e um evento de liberação do card, com ator da fábrica, é registrado.
- **AC-4** — Given um lock cujo processo dono segue vivo, when o ciclo roda, then o lock e o slot permanecem e nenhum evento é registrado.
- **AC-5** — Given um run que terminou normalmente, when ele é liberado, then o slot volta ao pool e o card pode ser pego de novo.
**Out of scope:** Escolher qual card roda (elegibilidade, horário, step-up); lançar o processo do agente; ligar a varredura a um ciclo do dispatcher; persistir locks entre reinícios do daemon; limites por estágio (refine/develop); ler a capacidade da config da máquina.
**Product fit:** Serve "toda ação é um evento com ator" (a liberação órfã fica no histórico do card) e "um daemon por máquina, único escritor do estado", que mantém a máquina do dono utilizável com até dez projetos. Fecha o gargalo G15 e é a base do dispatcher da fase 2 (S2-07). Não contradiz posicionamento nem o que está fora do produto.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Layers:** `daemon` (pool, locks, varredura) e `core` (só o novo tipo de evento).

**Contratos:**
- `core`: `EventType.LOCK_RELEASED`, com payload `reason` (`owner_dead`) e `pid`. Evento do card, ator `Factory("pool")`.
- `daemon`, em `sfv2.daemon`:
  - `fun interface ProcessProbe { fun isAlive(pid: Long, startedAt: Instant): Boolean }` e `SystemProcessProbe` (via `ProcessHandle`: vivo só se o PID existe e o instante de início confere, para não confundir PID reaproveitado).
  - `data class RunLock(val cardId: String, val pid: Long, val startedAt: Instant)`.
  - `sealed interface StartDecision`: `Started(lock)`, `PoolFull(capacity)`, `CardLocked(holder: RunLock)`.
  - `class RunPool(capacity: Int, store: CardEventStore, clock: Clock, probe: ProcessProbe, log: DaemonLog = StderrDaemonLog)`: `tryStart(cardId, pid, startedAt): StartDecision`, `release(cardId)`, `sweep(): List<RunLock>` (libera locks órfãos e devolve os liberados), `active(): List<RunLock>`. Métodos `@Synchronized`, como `CardCommands`.
- `tryStart` checa o lock do card antes da capacidade; recusa não escreve nada. `capacity` ≤ 0 lança `IllegalArgumentException` com a mensagem dizendo o valor.
- `sweep` grava `LOCK_RELEASED` via `store.recordChange` (card inalterado), publica no `EventStream` do daemon quando houver (parâmetro opcional `onEvent`) e loga `lock_released card=<id> pid=<pid>`. Card inexistente no store: libera o lock, loga `lock_release_orphan_card` e não grava evento.

**Camadas e módulos:** `core/src/main/kotlin/sfv2/core/Event.kt`; novos `daemon/src/main/kotlin/sfv2/daemon/RunPool.kt` e `ProcessProbe.kt`. Ligar o `sweep` a um ciclo do dispatcher fica para o card do dispatcher; aqui a varredura é uma função chamável.

**Migração:** nenhuma; locks vivem em memória e o novo tipo de evento só acrescenta valor ao enum.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Locks em memória, sem persistência: o daemon é o único dono dos runs e, ao reiniciar, não há processo filho a reconhecer; persistir exigiria migração sem ganho agora.
- Verificação do dono por PID + instante de início (G15): só o PID confundiria um PID reaproveitado com o run vivo.
- Liberação órfã vira `Event` do card (`LOCK_RELEASED`) em vez de só log: "registra evento" no AC-3 e o stream de eventos já alimenta app e notificações. Por isso o card toca `core` e o peso é heavy.
- `ProcessProbe` fica no `daemon` e não em `ports`: é só leitura do SO, sem segundo adaptador; os testes usam um fake da probe.
- Teste novo `RunPoolTest.kt`: nenhuma suíte do `daemon` cobre pool ou locks (`ModuleTest`, `ProtocolTest` e `SocketServerTest` cobrem módulo, protocolo e socket).
- Capacidade vem por parâmetro; ler `pool` da config da máquina fica para o card do dispatcher.
- Testes de `core` para o novo tipo não entram no plano: o enum é coberto pela serialização existente em `EventTest`, que o engenheiro estende sem AC próprio.

## Open questions
- none

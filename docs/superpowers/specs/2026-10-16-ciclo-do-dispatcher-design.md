# Ciclo do dispatcher — spec

**Issue:** #27 · **Branch:** `feat/27-ciclo-do-dispatcher` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** a fábrica trabalha sozinha, também com o app fechado; precisa de um ciclo no daemon que escolha o card certo na hora certa, sem estourar o WIP e sem rodar quando o dono pausou ou fora do horário.
**Audiência:** o desenvolvedor sozinho com muitos projetos (até dez numa máquina), que espera a fábrica andando sem configurar board nem vigiar.

**Critérios de aceitação**
- AC-1: Dado cards elegíveis em um estágio, quando o dispatcher roda, então escolhe o primeiro na ordem da coluna.
- AC-2: Dado um estágio com WIP no limite, quando o dispatcher roda, então nenhum card novo desse estágio começa; os demais estágios seguem.
- AC-3: Dado PAUSE ativo, quando o dispatcher roda, então nenhum card começa.
- AC-4: Dado o horário atual fora do horário de trabalho (consultado pelo gancho "dentro do horário?"), quando o dispatcher roda, então nenhum card começa.
- AC-5: Dado um card movido de coluna, quando o evento é registrado, então o dispatcher acorda e roda o ciclo sem esperar o próximo tick do relógio.
- AC-6: Dado um card já travado por outro run ou o pool de runs cheio, quando o dispatcher roda, então esse card não inicia e nenhum run duplicado é criado.
- AC-7: Dado um card escolhido, quando o dispatcher o inicia, então o estágio é acionado pela porta de estágio e o run fica travado no pool até ser liberado.

**Fora do escopo**
- Estágios concretos (refine, develop, validate rodando agentes): entram por uma porta.
- Configuração do horário de trabalho e step-up (#35); aqui só o gancho "dentro do horário?".
- UI e comandos de CLI/app para pausar.

**Aderência ao produto:** serve "a fábrica trabalha também com o app fechado" e "o daemon é o único escritor do estado". Não contradiz o posicionamento nem o fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `daemon` (pacote `sfv2.daemon`). Nenhum tipo novo de evento em `core`.

**Contratos:**
- `enum class DispatchStage(val column: Column)`: `REFINE(READY_FOR_REFINEMENT)`, `DEVELOP(READY_FOR_DEV)`, `VALIDATE(VALIDATION)`, nessa ordem (ordem do board).
- `fun interface StageLauncher { fun launch(stage: DispatchStage, cardId: String): Long }`: port local; inicia o estágio e devolve o pid do run. Os estágios concretos implementam a port em outros cards; nos testes, um fake.
- `fun interface PauseSwitch { fun isPaused(): Boolean }`, com `FilePauseSwitch(path: Path)` (existência do arquivo `PAUSE`).
- `fun interface WorkSchedule { fun isWorkTime(now: Instant): Boolean }`, com `AlwaysWorkTime`; a configuração real vem do #35.
- `data class DispatchLimits(val wip: Map<DispatchStage, Int>)`: ausente ou `0` = sem limite (mesma convenção do `wip: 0` do `factory.yml`).
- `class Dispatcher(store: CardEventStore, pool: RunPool, launcher: StageLauncher, pause: PauseSwitch, schedule: WorkSchedule, limits: DispatchLimits, clock: Clock, tick: Duration, log: DaemonLog = StderrDaemonLog) : AutoCloseable`:
  - `fun runCycle(): List<DispatchedRun>` (`DispatchedRun(stage, cardId)`), `@Synchronized`: sai vazio com PAUSE ou fora do horário; senão, por estágio na ordem do enum, lê os cards na coluna do estágio (ordem de criação de `store.cards()`), pula os travados (`pool.isLocked`), para o estágio ao atingir o WIP (runs ativos do estágio, contados pelo próprio dispatcher e podados contra `pool.active()`) e para tudo com o pool cheio (`pool.hasRoom()`); inicia com `launcher.launch` e `pool.tryStart(cardId, pid, clock.instant())`. O ciclo é o único chamador de `tryStart`, então checar e iniciar não corre.
  - `fun wake()` acorda o laço; `fun start()` / `close()` controlam a thread do laço, que roda `runCycle` a cada `tick` ou ao `wake()`.
  - `fun onEvent(event: StoredEvent)`: chama `wake()` quando `event.event.type == EventType.CARD_MOVED`.
- `RunPool`: novos `fun isLocked(cardId: String): Boolean` e `fun hasRoom(): Boolean` (`@Synchronized`).
- `EventStream`: novo `fun addListener(listener: (StoredEvent) -> Unit)`, chamado em `publish` sem bloquear; a fiação `addListener(dispatcher::onEvent)` na composição (`Main.kt`) fica para quando o dispatcher for criado lá.
- Log `key=value` só com `card`, `stage`, `pid`; nunca título.

**Arquivos:** novos `daemon/src/main/kotlin/sfv2/daemon/Dispatcher.kt` e `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt`; alterados `RunPool.kt` e `EventStream.kt`.

**Migração:** nenhuma; sem mudança de store, evento ou contrato público.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso heavy por ter 7 critérios de aceitação (regra do peso), embora toque uma só camada.
- Ordem entre estágios: a do board (refine, develop, validate), lida literalmente de "ordem da coluna"; é a ordem do enum `DispatchStage`, fácil de inverter.
- AC-7 sem evento de início: não há tipo de evento de run em `core` e criá-lo tornaria o card de duas camadas; o log estruturado e o lock do pool dão a rastreabilidade (mesmo corte do #24).
- Horário de trabalho e PAUSE entram por ports (`WorkSchedule`, `PauseSwitch`); a configuração real do horário é do #35, e `AlwaysWorkTime` é o padrão.
- WIP por estágio vem de `DispatchLimits` (parâmetro), não do `factory.yml`: a leitura da config é de outro card.
- Dependência "Blocked by" não é avaliada: o store não guarda dependências; fica para o card de importação/forge.
- Teste novo `DispatcherTest.kt`: nenhuma suíte do `daemon` cobre o dispatcher; `isLocked`/`hasRoom`/`addListener` são exercitados por ele.
- `StageLauncher` devolve o pid e o lock entra depois do launch porque `RunPool.tryStart` exige o pid; `hasRoom`/`isLocked` evitam lançar sem vaga.

## Open questions
- none

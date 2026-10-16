# Limites de custo — spec

**Issue:** #34 · **Branch:** `feat/34-limites-de-custo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** a fábrica trabalha sozinha, inclusive à noite e com o app fechado; sem teto, um card em laço ou um dia de muitos runs gasta sem o dono ver. O custo já é medido por run (eventos `RUN_COST_RECORDED`); falta agir quando ele passa de um limite.

**Público:** o desenvolvedor sozinho com muitos projetos (e o dentro de empresa), que deixa a fábrica rodando fora do horário.

**Critérios de aceitação**
- AC-1: Dado um card cujo custo acumulado (soma dos `RUN_COST_RECORDED` dele) alcança o limite por card, quando o daemon avalia o custo após um run, então o card passa a bloqueado, com o motivo (limite do card, valor gasto e limite) e um evento novo de bloqueio por custo com o id do card.
- AC-2: Dado um card bloqueado por limite de custo, quando o daemon escolhe o próximo trabalho, então nenhum run novo desse card começa.
- AC-3: Dado o custo somado de todos os runs do dia (dia pelo `Clock` do daemon) igual ou acima do limite diário, quando o daemon vai iniciar um run de qualquer card, então o run não começa e o card permanece onde está, sem ser bloqueado.
- AC-4: Dado o limite do dia atingido, quando o dia seguinte começa, então runs voltam a ser iniciados e o custo do dia conta de zero.
- AC-5: Dado um custo abaixo dos dois limites, quando o daemon decide iniciar um run, então o comportamento é o de hoje, sem evento novo.
- AC-6: Dado que nenhum limite foi informado, quando o daemon decide iniciar runs, então não há restrição de custo.
- AC-7: Dado um run em andamento quando o limite do dia é cruzado, então esse run termina normalmente; o limite só impede começar runs novos.

**Fora de escopo:** ler os limites de arquivo de config (outro card; aqui entram por parâmetro); interromper run em andamento; desbloqueio pelo dono e UI/app; limite por projeto; alerta de aproximação do limite; medir custo (feito em #33).

**Aderência ao produto:** serve ao custo medido das transcrições e à fábrica que trabalha fora do horário do dono, com decisão voltando a ele (card bloqueado com motivo e evento, como evento com ator). Não contradiz posicionamento nem exclusões. O custo continua uma estimativa.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core`: `EventType.CARD_BLOCKED` no catálogo. Payload: `reason` (`card_cost_limit`), `spent_usd`, `limit_usd`, `estimate=true`. Ator: `Actor.Factory("cost")`.
- `daemon`, `CostLimits(perCardUsd: BigDecimal?, perDayUsd: BigDecimal?)`: `null` = sem limite (`CostLimits.NONE`). Valores em `BigDecimal`.
- `daemon`, `CostBudget(store, recorder: EventRecorder, limits, clock, zone: ZoneId, log)`:
  - `onRunFinished(cardId)`: soma os `RUN_COST_RECORDED` do card (`cost_usd`; `UNKNOWN` conta 0); se `>= perCardUsd` e o card ainda não está bloqueado, grava `CARD_BLOCKED` pelo `EventRecorder` (nunca `store.recordChange` direto). Log `key=value` só com ids.
  - `isBlocked(cardId): Boolean`: há `CARD_BLOCKED` depois do último `CARD_MOVED` do card (mover o card é a saída do dono; desbloqueio dedicado fica para outro card).
  - `dayLimitReached(): Boolean`: soma de `cost_usd` dos `RUN_COST_RECORDED` com `at` na data corrente (`clock` + `zone`) `>= perDayUsd`. O dia seguinte zera por construção (a data muda).
- `daemon`, `Dispatcher`: novo parâmetro com padrão `launchGuard: LaunchGuard = LaunchGuard.Open` (`fun interface LaunchGuard { fun allows(cardId: String): Boolean }`; `CostBudget` implementa). Em `runCycle`, `dayLimitReached` interrompe o ciclo antes de iniciar qualquer run; card bloqueado é pulado (`continue`). Sem limites = `NONE`, comportamento atual.
- Quem chama `onRunFinished` é o fim do run, logo depois do `RunCostRecorder.record`; ligar isso ao fim real do run fica com o card do dispatcher/runtime (aqui só o ponto de chamada testado).

**Camadas e módulos:** `core` (1 enum) e `daemon` (`CostBudget.kt`, `Dispatcher.kt`). Sem mudança em `store`, `ports` nem `adapters`.

**Migração:** nenhuma (evento novo; `payload` é mapa livre).

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-1 | core | `core/src/test/kotlin/sfv2/core/EventTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: duas camadas (core e daemon) e 7 critérios, apesar do `light` esperado na issue.
- Evento novo `CARD_BLOCKED` em vez de campo no `Card`: o catálogo de eventos é a fonte; "bloqueado" deriva dos eventos (bloqueio depois do último movimento), sem mudar o modelo do card nem o `store`.
- Saída do bloqueio: mover o card (ação do dono); desbloqueio dedicado está fora de escopo.
- Custo `UNKNOWN` conta 0 nos limites e `PARTIAL` conta o subtotal: o limite é sobre estimativa conhecida; o custo desconhecido já é sinalizado no evento do #33.
- O limite do dia só olha runs já encerrados (o custo só existe ao fim do run); runs em andamento podem ultrapassá-lo (AC-7), aceito.
- Limites entram por parâmetro (`CostLimits`), como `DispatchLimits`: a leitura do `factory.yml` é de outro card.
- Dia pelo `Clock` e um `ZoneId` por parâmetro (padrão do sistema na composição), para o teste ser determinístico.
- Sem arquivo de teste novo: `RunCostTest.kt` cobre custo e bloqueio, `DispatcherTest.kt` cobre o início de runs.
- Soma do dia varre os eventos via `store`; suficiente no volume atual, índice por data fica para quando medir.

## Open questions
- none

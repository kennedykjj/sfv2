# Horário de trabalho e step-up de modelo — spec

**Issue:** #35 · **Branch:** `feat/35-horario-de-trabalho-e-step-up-de-modelo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** a fábrica trabalha sozinha, mas o dono precisa limitar quando ela inicia runs, e um gate de develop que falha por falta de capacidade do modelo hoje repete o mesmo modelo, gastando runs sem chance maior de passar.

**Audiência:** o desenvolvedor sozinho com muitos projetos (e, igualmente, o desenvolvedor dentro de uma empresa que quer a fábrica só em certos horários).

**Critérios de aceitação**
- AC-1: Dado `work_hours` no `factory.yml` com início/fim, dias da semana e fuso, quando o dispatcher avalia um instante dentro da janela, então um run pode iniciar; fora dela, nenhum run inicia.
- AC-2: Dado uma janela que cruza a meia-noite (ex.: 22:00 a 06:00), quando o instante está depois das 22:00 ou antes das 06:00 em um dia de semana configurado, então conta como dentro da janela (o dia vale pelo início da janela).
- AC-3: Dado um `factory.yml` sem `work_hours`, quando o dispatcher avalia qualquer instante, então está sempre dentro da janela.
- AC-4: Dado um `work_hours` inválido (horário malformado, dia desconhecido, fuso inexistente), quando a configuração é lida, então falha com erro que nomeia o campo inválido.
- AC-5: Dado um card em Coding cujo gate do develop falhou por capacidade (exit_code diferente de zero), quando o próximo run do card é montado, então usa o próximo modelo da escada.
- AC-6: Dado um card já no último modelo da escada, quando um gate do develop falha por capacidade, então o modelo permanece o mesmo, sem erro.
- AC-7: Dado uma falha de ambiente (timeout ou start_error) ou uma falha de gate fora de Coding, quando o próximo run do card é montado, então o modelo não sobe.
- AC-8: Dado dois cards, quando o gate de um falha por capacidade, então só o degrau desse card sobe; o do outro permanece. Quando um card sai de Coding, seu degrau volta ao modelo padrão.

**Fora de escopo:** UI; persistir degraus entre reinícios do daemon; o launcher concreto; notificações e horário silencioso (S6-08); tela de configuração do dono.

**Encaixe no produto:** serve "a fábrica trabalha também com o app fechado e fora do horário do dono" e "gates são scripts, não a palavra do agente" (a decisão de subir vem do exit_code do gate). Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `daemon` (pacote `sfv2.daemon`). Nenhum evento novo em `core`.

**Contratos:**
- `class WorkHours(start: LocalTime, end: LocalTime, days: Set<DayOfWeek>, zone: ZoneId) : WorkSchedule`: `isWorkTime(now)` converte para `zone`; se `start < end` vale `start <= t < end` no dia; se `start > end` (cruza a meia-noite) vale `t >= start` no dia D ou `t < end` no dia seguinte a D, com D em `days`; `start == end` é inválido.
- `WorkHours.from(raw: Map<String, Any?>?): WorkSchedule`: lê `work_hours.start` / `end` (`HH:mm`), `days` (`mon`..`sun`; padrão todos) e `timezone` (padrão `UTC`); ausente → `AlwaysWorkTime`; inválido → `InvalidWorkHoursException(field, message)` (`RuntimeException`) nomeando o campo (ex.: `work_hours.days`), sem ecoar o valor inteiro do arquivo.
- `class ModelStepUp(ladder: List<String>, store: CardEventStore)`: `fun modelFor(cardId: String): String` (degrau do card; padrão = `ladder.first()`); `fun onEvent(event: StoredEvent)`: em `GATE_FINISHED` com `verdict=FAIL`, `reason=exit_code` e card hoje em `Column.CODING`, sobe um degrau até o último; `reason` `timeout`/`start_error`, ou card fora de Coding, não sobe (G9: só falha de capacidade); em `CARD_MOVED` com `payload["from"] == "CODING"`, remove o degrau do card. Estado em `ConcurrentHashMap`; `onEvent` não toma lock próprio nem bloqueia (roda dentro de `EventStream.publish`).
- `ladder` vazia → `require` falha na construção.
- Fiação: `Dispatcher` já recebe `WorkSchedule`; `addListener(modelStepUp::onEvent)` e a leitura de `work_hours` do `factory.yml` entram na composição (`Main.kt`) quando o dispatcher for criado lá; o launcher concreto (outro card) chama `modelFor(cardId)`.
- Log `key=value` só com `card` e `step`.

**Arquivos:** novos `daemon/src/main/kotlin/sfv2/daemon/WorkHours.kt`, `daemon/src/main/kotlin/sfv2/daemon/ModelStepUp.kt`, `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt`, `daemon/src/test/kotlin/sfv2/daemon/ModelStepUpTest.kt`.

**Migração:** nenhuma; sem mudança de store, evento ou contrato público.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModelStepUpTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModelStepUpTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModelStepUpTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ModelStepUpTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso heavy pela regra (8 critérios, mais de 6), embora toque uma só camada; o dono pode baixar para standard.
- Capacidade vs. ambiente decidida pelo `reason` do evento `GATE_FINISHED` (`exit_code` sobe; `timeout`/`start_error` não), como pede o G9.
- Degrau em memória e perdido no reinício do daemon (volta ao padrão, o lado barato); persistir fica fora do escopo.
- Escada de modelos entra por parâmetro (`ladder`); a leitura dela do `factory.yml` é da composição, não deste card.
- Dia da janela que cruza a meia-noite vale pelo dia de início (AC-2); `start == end` é inválido em vez de "24 h".
- Testes novos `WorkHoursTest.kt` e `ModelStepUpTest.kt`: nenhuma suíte do `daemon` cobre horário nem modelo.
- `ModelStepUp` lê `store.card` para saber se o card está em Coding: o evento de gate não traz a coluna.

## Open questions
- none

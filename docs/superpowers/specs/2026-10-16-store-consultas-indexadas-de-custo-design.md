# Store: consultas indexadas de custo do dia e bloqueio por card — spec

**Issue:** #151 · **Branch:** `feat/151-store-consultas-indexadas-de-custo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** a cada ciclo o dispatcher lê e decodifica todo o histórico de eventos para calcular o custo do dia e decidir o bloqueio por card. O trabalho cresce com o histórico e, depois de semanas, passa a dominar o ciclo, atrasando a fábrica que deve rodar sempre ligada.
**Audience:** o desenvolvedor sozinho com muitos projetos (até dez numa máquina, um daemon sempre ligado); vale também para o desenvolvedor na empresa.
**Acceptance criteria**
- **AC-1** — Dado o histórico de eventos, quando o daemon calcula o custo do dia, então a consulta lê só os eventos `RUN_COST_RECORDED` do dia pelo banco (índice por tipo e data), sem decodificar os demais.
- **AC-2** — Dado um card com muitos eventos, quando o dispatcher decide se ele está bloqueado, então a consulta usa o último `CARD_BLOCKED` e o último movimento do dono por consulta indexada, não a lista inteira do card.
- **AC-3** — Dado um banco da versão anterior com dados, quando o store abre, então migra aditivamente (índice novo) sem perder dados, provado por teste que abre um banco com dados.
- **AC-4** — Dado o mesmo histórico, quando o custo do dia e o bloqueio são calculados antes e depois da mudança, então o resultado é o mesmo e os testes de limite de custo do #34 passam sem mudança de comportamento.
**Out of scope:** cache em memória do custo do dia.
**Product fit:** serve ao daemon sempre ligado como único escritor do estado e ao limite de custo medido (custo do dia e por card) sem degradar com o uso; não contradiz posicionamento nem o que o produto não é.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos tocados (store, `CardEventStore`):**
- Novo `costEventsBetween(from: Instant, toExclusive: Instant): List<StoredEvent>`: só eventos `RUN_COST_RECORDED` com `at` em `[from, toExclusive)`, em ordem de `seq`. O filtro de tipo e de data roda no SQL; só essas linhas passam por `EventJson.decode`.
- Novo `lastSeq(cardId: String, type: EventType, actor: Actor? = null): Long?`: maior `seq` do card para o tipo (e, se dado, para o ator), ou null. Lê só `seq`, sem decodificar payload.
- `eventsAfter` e `events` ficam como estão (outros consumidores).

**Consultas e índices (`Schema.sq`):**
- `costEventsBetween`: `WHERE type = 'RUN_COST_RECORDED' AND at >= :from AND at < :to ORDER BY seq`, atendida por `CREATE INDEX event_type_at ON event(type, at)`.
- `lastSeq`: `SELECT MAX(seq) ... WHERE card_id = :cardId AND type = :type [AND actor = :actor]`, atendida por `CREATE INDEX event_card_type_actor ON event(card_id, type, actor, seq)`.
- `at` é guardado como `Instant.toString()` (texto, tamanho da fração varia). Os limites da consulta são o texto do `Instant` **sem o `Z` final** (ex.: `2026-10-15T03:00:00`): assim `...00Z` e `...00.5Z` ordenam corretamente em relação ao limite. Não comparar com o `Z`.

**Migração (expand, aditiva):** `SCHEMA_VERSION` 5 → 6; novo `migrations/5.sqm` com os dois `CREATE INDEX`; `Schema.sq` ganha os mesmos índices (banco novo e migrado terminam com o mesmo schema). Sem mudança de tabela nem de dado. Os testes que afirmam `user_version` 5 passam a afirmar 6.

**Daemon (`CostBudget`):**
- `dayLimitReached()`: calcula `[início do dia em zone, início do dia seguinte)` a partir do `Clock` e usa `costEventsBetween`; agrupa por card e soma como hoje (`sum` inalterado, `unpriced` continua fechando o dia).
- `isBlocked(cardId)`: `lastSeq(card, CARD_BLOCKED)` null → false; senão bloqueado enquanto não houver `lastSeq(card, CARD_MOVED, Actor.Owner)` maior que ele. Mesma regra de hoje, sem listar o card inteiro.
- `onRunFinished` não muda (lê os eventos do card uma vez por run, não por ciclo).

**Camadas e módulos:** `store` (`CardEventStore.kt`, `Schema.sq`, `migrations/5.sqm`, `Database.kt`) e `daemon` (`CostBudget.kt`). Nenhuma porta nova.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: a spec toca 2 camadas (store e daemon); a regra de peso prevalece sobre o "standard" esperado na issue.
- Dois índices, um por consulta: `(type, at)` para o custo do dia e `(card_id, type, actor, seq)` para o último bloqueio e o último movimento do dono; `event_card_id` existente fica.
- Limites de data sem o `Z` final, porque `at` é texto com fração de tamanho variável e a ordem lexicográfica com `Z` erra na fronteira.
- Prova de uso do índice por `EXPLAIN QUERY PLAN` no teste do store, e prova de "sem decodificar os demais" com um evento de outro tipo de payload inválido que não pode quebrar a consulta.
- Sem arquivo de teste novo: `ModuleTest.kt` (store) e `RunCostTest.kt` (daemon) já cobrem os módulos.
- AC-4 é regressão: os testes do #34 em `RunCostTest.kt` passam sem alteração de expectativa.

## Open questions
- none

# Daemon: gravar e publicar evento sob um único lock — spec

**Issue:** #134 · **Branch:** `feat/134-gravar-e-publicar-evento-sob-um-unico-lock` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o stream de eventos descarta qualquer evento com `seq` menor ou igual ao último enfileirado. Hoje `CardCommands`, `RunPool.sweep` e `GateExecutor` gravam e publicam cada um sob seu próprio monitor (o gate, sem lock), então um evento de `seq` menor pode ser publicado depois de um maior e some em silêncio para os assinantes vivos. A perda só se recupera relendo o banco.

**Audiência:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa, que dependem do app e da CLI mostrando a trilha de eventos completa e em ordem.

**Critérios de aceitação**
- AC-1: Dado dois autores gravando e publicando ao mesmo tempo (comando de card, varredura do pool e gate), quando os eventos são emitidos, então todo assinante recebe todos em ordem estrita de `seq`, sem perda nem duplicata.
- AC-2: Dado um único ponto de escrita de eventos no daemon, quando o código é inspecionado, então nenhum código chama `store.recordChange` e `events.publish` fora dele (teste de arquitetura ou revisão estática no teste).
- AC-3: Dado a corrida reproduzida em teste (dois threads, publicações forçadas fora de ordem), quando o teste roda, então falha sem a correção e passa com ela.

**Fora do escopo:** mudar o protocolo do stream ou o formato do evento.

**Aderência ao produto:** serve ao princípio "toda ação é um evento com ator" e ao daemon como único escritor do estado: a trilha só vale se nenhum evento se perde. Não contradiz posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada única: `daemon`. Sem mudança de protocolo, evento ou schema; sem migração.
- Novo `daemon/src/main/kotlin/sfv2/daemon/EventRecorder.kt`: `class EventRecorder(store: CardEventStore, events: EventStream)` com `@Synchronized fun record(cardId: String, card: Card, event: Event): StoredEvent`, que chama `store.recordChange` e `events.publish` sob o mesmo monitor e devolve o `StoredEvent`. É o único chamador de `recordChange` e `publish` no código de produção do daemon.
- `CardCommands.kt`: cria `val recorder = EventRecorder(store, events)` (público, para ligar o pool e o gate); `create` e `move` usam `recorder.record`. O `@Synchronized` de `handle` continua (atomicidade de ler-checar-gravar do move); a ordem de locks é sempre comando → recorder, e o recorder nunca pega outro lock, então não há deadlock.
- `RunPool.kt`: o parâmetro `onEvent: (StoredEvent) -> Unit` é trocado por `recorder: EventRecorder`; `recordRelease` usa `recorder.record`.
- `GateExecutor.kt`: o parâmetro `events: EventStream` é trocado por `recorder: EventRecorder`; `execute` usa `recorder.record`.
- `Main.kt`/`Module.kt`: nada liga hoje o pool e o gate; a ligação fica no #27.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/EventRecorderTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/EventRecorderTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/EventRecorderTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Um `EventRecorder` com um monitor só, em vez de compartilhar o lock de `CardCommands`: o pool e o gate não conhecem o comando de card, e um lock próprio do recorder serve aos três sem acoplá-los.
- Arquivo novo `EventRecorderTest.kt`: nenhuma suíte existente cobre o recorder; `RunPoolTest` e `GateExecutorTest` só ajustam o construtor.
- AC-2 como teste estático que varre `daemon/src/main` procurando `recordChange(` e `.publish(` fora de `EventRecorder.kt` (e da definição em `EventStream.kt`).
- AC-3: o teste força a intercalação com um `CardEventStore` que pausa entre gravar e devolver, de modo que sem o lock a publicação sai fora de ordem.
- Peso `standard`: uma camada, 4 arquivos, 3 ACs, mas toca concorrência.

## Open questions
- none

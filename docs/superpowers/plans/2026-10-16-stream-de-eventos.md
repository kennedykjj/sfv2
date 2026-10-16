# Stream de eventos — plano (#17)

Spec: `docs/superpowers/specs/2026-10-16-stream-de-eventos-design.md`. Base: main com o #110 (coluna `seq`).

## Interface acordada (para QA e engenheiro)

- `CardEventStore.recordChange(...)` devolve `Long` (o `seq` gravado, lido na mesma transação).
- `CardEventStore.eventsAfter(seq: Long): List<StoredEvent>`; `StoredEvent(seq: Long, cardId: String, event: Event)`.
- `CardCommands(store, clock, log, subscriberQueueLimit: Int = 1000)` cria e expõe `val events: EventStream`.
- `EventStream(store, queueLimit, log)`: `publish(StoredEvent)` não bloqueante; `subscribe(connection, after: Long?)`.
- `SocketServer` entrega a conexão ao `commands.events` ao receber `Request.Subscribe` e deixa de ler comandos nela.
- Protocolo: `{"cmd":"events.subscribe"[,"after":<seq>]}` → `{"ok":true,"result":"subscribed"}` e depois uma linha `{"event":{...}}` por evento; `after` inválido → `{"ok":false,"error":"invalid after"}`; assinante lento → `{"error":"subscriber too slow"}` e fecha só aquela conexão.

## Tarefas

### 1. store (engineer) — `store/`
- `CardEventStore.kt`: `recordChange` devolve o `seq`; `eventsAfter`; `StoredEvent`.
- `Schema.sq`: query `eventsAfter` (`WHERE seq > :seq ORDER BY seq`) e leitura do `seq` inserido (`last_insert_rowid()` na mesma transação).
- Testes do store ficam em `store/src/test/kotlin/sfv2/store/ModuleTest.kt` (já existente), se o engenheiro precisar de cobertura própria.

### 2. daemon (engineer) — `daemon/`, depende da 1
- Novo `EventStream.kt`: fila limitada e escritor próprio (thread virtual) por assinante; `publish` é `offer`, fila cheia remove o assinante com `{"error":"subscriber too slow"}`; inscrição registra o assinante antes do replay e descarta `seq` já enviado.
- `CardCommands.kt`: publica depois do commit, dentro do `@Synchronized`.
- `Protocol.kt`: `Request.Subscribe(after: Long?)`, parse e render da linha de evento.
- `SocketServer.kt`: despacho do `Subscribe`; `Main.kt` só se precisar de injeção.
- Logs só com `seq` e id do card.

## Dono dos arquivos
Tudo em `store/` e `daemon/` (agente `engineer`); testes de aceitação em `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` (QA).

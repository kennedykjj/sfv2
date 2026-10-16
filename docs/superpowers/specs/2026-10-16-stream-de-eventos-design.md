# Stream de eventos — spec

**Issue:** #17 · **Branch:** `feat/17-stream-de-eventos` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** App, notificações e assistente precisam reagir a mudanças na fábrica sem polling; hoje não há como assinar os eventos que o daemon grava.
**Audience:** O desenvolvedor sozinho com muitos projetos (e o que está dentro de uma empresa), que usa app, CLI e assistente como clientes do mesmo daemon.
**Acceptance criteria**
- **AC-1** — Dado dois clientes inscritos no stream, quando um card é movido, então os dois recebem o mesmo evento, na mesma ordem.
- **AC-2** — Dado um cliente que se inscreve informando o id do último evento visto, quando há eventos posteriores a ele, então recebe todos os perdidos, em ordem, antes de qualquer evento novo, sem perda nem duplicata na transição.
- **AC-3** — Dado um cliente lento (que não consome), quando eventos são gerados, então os outros clientes seguem recebendo sem atraso e o daemon continua gravando eventos sem esperar por ele.
- **AC-4** — Dado um cliente lento que ficou muito atrás, quando o limite de espera é excedido, então o stream dele é encerrado e ele pode se reconectar pelo último evento visto (AC-2) sem perder eventos.
- **AC-5** — Dado um cliente inscrito sem informar último evento, quando novos eventos são gravados, então recebe só os eventos a partir da inscrição.
- **AC-6** — Dado um cliente que se desconecta, quando o stream é encerrado, então o daemon libera os recursos dele e os demais clientes não são afetados.
**Out of scope:** Filtros por projeto ou tipo de evento; garantia de entrega além do replay pelo último id; tela do app e notificações em si (consumidores do stream); acesso por rede ou MCP (só socket local); autenticação entre clientes.
**Product fit:** Serve "toda ação é um evento com ator" e "daemon único escritor, clientes falam por socket local": o stream é a forma de os clientes verem o estado sem ser donos dele. Não contradiz o posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contrato (protocolo JSON por linha, `daemon/.../Protocol.kt`):**
- `{"cmd":"events.subscribe"}` ou `{"cmd":"events.subscribe","after":<seq>}` → `{"ok":true,"result":"subscribed"}`; a conexão vira stream de mão única e passa a receber uma linha por evento: `{"event":{"seq":<n>,"id","card","at","actor","type","payload":{...}}}`. Com `after`, primeiro vêm os eventos com `seq > after` gravados, depois os novos, sem lacuna nem duplicata (o cliente guarda o último `seq`). `after` ausente → só eventos gravados depois da inscrição. `after` inválido → `{"ok":false,"error":"invalid after"}`.
- Cliente lento removido: o daemon envia `{"error":"subscriber too slow"}` e fecha só aquela conexão; o cliente reconecta com `after`.
- `seq` é a coluna `seq` do evento criada pelo #110; ela é o cursor.

**Camadas e arquivos:**
- `store` — `store/src/main/kotlin/sfv2/store/CardEventStore.kt`: `recordChange` passa a devolver o `seq` gravado (`Long`); nova `eventsAfter(seq: Long): List<StoredEvent>` (`StoredEvent(seq, cardId, event)`); `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: query `eventsAfter` (`WHERE seq > :seq ORDER BY seq`) e leitura do `seq` inserido na mesma transação.
- `daemon` — novo `EventStream.kt`: cada assinante tem fila limitada e um escritor próprio (thread virtual); `publish` é `offer` não bloqueante, e fila cheia remove o assinante. Inscrição registra o assinante antes de ler o replay do store e descarta `seq` já enviado, o que dá ordem total sem lacuna. `CardCommands.kt` publica o evento depois do commit, dentro do `@Synchronized` (a ordem de publicação é a ordem de `seq`). `Protocol.kt` (`Request.Subscribe`, render da linha de evento), `SocketServer.kt` (ao receber `Subscribe`, entrega a conexão ao `EventStream` e deixa de ler comandos nela), `Main.kt` (injeção).
- `core` não muda.

**Migração:** nenhuma neste card (depende do esquema v3 do #110).

## Test plan
_Tech Lead_

Testes em processo pelo `SocketServer` com `Database` temporário e `Clock` fixo; cliente lento simulado sem ler o socket; sem sleeps (sincronização por leitura bloqueante com timeout).

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca 2 camadas (daemon e store), pela regra de peso.
- O cursor é o `seq` do evento (ordem total atribuída pelo banco, #110), não o id UUID: o id não tem ordem e o replay precisa de "tudo depois de".
- Fila limitada por assinante com remoção em vez de buffer ilimitado: é o que torna o AC-3 possível sem travar a gravação; o limite (padrão 1000 eventos) é constante do daemon.
- Publicar depois do commit e dentro do `@Synchronized` de `CardCommands`: todos os assinantes veem a mesma ordem e nunca um evento não gravado.
- Replay lê o store inteiro após `after` de uma vez: aceito na fase 1 (poucos eventos); paginação fica para quando o volume pedir.
- Sem arquivo de teste novo: `SocketServerTest.kt` já cobre o servidor em processo.
- Nunca logar payload nem título: só `seq` e id do card nos logs do stream.

## Open questions
- **Ordem:** A (recommended) esperar o #110 entrar na main antes de desenvolver o #17, pois o cursor é o `seq` dele, ou B cursor por `rowid` e adaptar depois — B força retrabalho do replay quando a migração recriar a tabela. O #17 hoje só está bloqueado pelo #16.

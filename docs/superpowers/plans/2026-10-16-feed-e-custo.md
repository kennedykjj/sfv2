# Plano — Feed e custo (#68)

Decisão da pergunta aberta: opção A. O daemon entrega os limites dentro de `cost.summary` (campo `limits`); não há comando `cost.limits` separado, para o app fazer uma única consulta e o destaque vir do mesmo cálculo do `CostBudget`.

## Tarefas

### T1 — daemon (agente: engineer)
Dependências: nenhuma.
- `Protocol.kt`: `Request.EventsRecent(limit)` (limite máximo 2000, inválido → `Rejected`), `Request.CostSummary(from, to)` (instantes ISO inválidos → `Rejected`); respostas `Response.EventsRecentResult(events, truncated)` e `Response.CostSummaryResult(...)`, no formato de `renderEvent` e no contrato da spec.
- `SocketServer.kt`: `dispatch` trata os dois comandos (read-only).
- `CardEventStore` (camada store, apenas se faltar): consulta dos últimos N eventos por `seq` decrescente com limite N+1 para saber `truncated`. Se for necessário mexer no `store`, a tarefa vira da camada store com o mesmo engineer.
- `CostSummaryQuery.kt`: soma por dia/por card com `store.costEventsBetween` e a mesma conta de `CostBudget` (extrair a soma e a decisão "atingido" para um único lugar usado pelos dois); limites de `DispatcherSettings.limits`; `dayReached`, `cardsReached`, `status` KNOWN|PARTIAL|UNKNOWN|NONE.
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/{Protocol,SocketServer,CostBudget,CostSummaryQuery}.kt`.

### T2 — app (agente: engineer)
Dependências: T1 (contrato do protocolo).
- `Tokens.kt` (extraído de `ConnectionScreen`, `internal`, com `edge-hi`, `faint`, `bad`), `FeedEvent`, `FeedState`, `EventFeedClient` (janela `events.recent`, assina com `after=<último seq>`, descarta `seq` visto, reconecta), `CostSummaryView` (só formata), `CostSummarySource` (`fun interface`; falha → sem limites), `FeedScreen`, `CostPanel`, textos em `Texts`.
- `Main.kt`: mostra `FeedScreen` quando `Connected`.
- Arquivos: `app/src/main/kotlin/sfv2/app/*`.

## Testes de aceitação (QA)
- `app/src/test/kotlin/sfv2/app/FeedTest.kt` (novo): AC-1..5, 7..11.
- `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` (existente ou novo conforme o módulo): AC-6.

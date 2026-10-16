# Relatório de lead time e custo por card — spec

**Issue:** #61 · **Branch:** `feat/61-relatorio-de-lead-time-e-custo-por-card` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** o dono não consegue comparar a sfv2 com a V0 com números: falta saber quanto tempo cada card gasta em cada estágio e quanto custa.
**Audience:** o desenvolvedor sozinho com muitos projetos (e o desenvolvedor em empresa que precisa justificar a fábrica com dados).
**Acceptance criteria**
- **AC-1** — Dado um card em Done com eventos CARD_CREATED e CARD_MOVED, quando o relatório é gerado, então mostra o lead time em cada estágio (coluna), contado entre eventos consecutivos a partir da criação; cards fora de Done não aparecem.
- **AC-2** — Dado um card em Done com eventos RUN_COST_RECORDED, quando o relatório é gerado, então mostra o custo estimado somado, rotulado como estimativa.
- **AC-3** — Dado um card em Done cujo custo é desconhecido ou parcial (run sem custo medido), quando o relatório é gerado, então o custo aparece sinalizado como desconhecido ou parcial, nunca como zero.
- **AC-4** — Dado cards em Done, quando um cliente pede o relatório pelo socket do daemon em formato CSV, então recebe uma linha por card em Done, uma coluna por estágio em segundos e as colunas de custo (valor e sinalização).
**Out of scope:** comando de CLI (follow-up); tela no app; cards fora de Done; filtros por período; comparação automática com a V0.
**Product fit:** serve "Custo medido das transcrições de cada run" e "Custo é estimativa, sempre rotulada" (Positioning/Constraints); tempo vem dos eventos, o daemon é a fonte única. Não contradiz posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos tocados (daemon):**
- Novo `CardReport` (`daemon/.../CardReport.kt`), puro: `CardReport.build(cards: List<StoredCard>, eventsOf: (String) -> List<Event>): List<CardReportRow>` e `CardReport.toCsv(rows): String`. Sem IO nem relógio.
- `CardReportRow(cardId: String, title: String, stageSeconds: Map<Column, Long>, costUsd: BigDecimal?, costStatus: CostStatus)`; `enum CostStatus { KNOWN, PARTIAL, UNKNOWN, NONE }`.
- Lead time por estágio: percorre `CARD_CREATED` e `CARD_MOVED` em ordem de `seq`; o tempo na coluna `to` de um movimento é `at` do próximo movimento menos o seu `at` (a coluna inicial vem de `CARD_CREATED`). O estágio final (Done) não tem duração. Só cards cuja coluna atual é `DONE`.
- Custo: soma de `cost_usd` dos `RUN_COST_RECORDED` com status `KNOWN`/`PARTIAL`; `costStatus` = `KNOWN` se todos `KNOWN`, `PARTIAL` se algum `PARTIAL`, `UNKNOWN` se algum `UNKNOWN` (prevalece), `NONE` sem eventos. `costUsd` é `null` (célula vazia no CSV) quando `UNKNOWN` ou `NONE`, nunca zero; com `PARTIAL` mostra a soma do que foi medido, sinalizada.
- CSV: cabeçalho `card_id,title,<uma coluna por Column em segundos: nome da coluna em snake_case>,cost_usd_estimate,cost_status`; título entre aspas com escape RFC 4180 e neutralizado contra fórmula de planilha (prefixo `'` se começa com `= + - @`).
- Protocolo (`Protocol.kt`): `Request.ReportCsv` (`"cmd":"report.csv"`, leitura, sem ator nem prova) e `Response.ReportResult(csv: String)` serializado como `{"ok":true,"csv":"..."}`. `CardCommands.handle` resolve com `store.cards()`/`store.events(id)`; `SocketServer.kt` roteia o novo request como os demais de card.

**Camadas e módulos:** só `daemon` (`CardReport.kt` novo, `Protocol.kt`, `CardCommands.kt`, `SocketServer.kt`). Sem mudança em store, schema, config ou dependência. Sem migração.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (daemon), mas 4 arquivos (`CardReport.kt`, `Protocol.kt`, `CardCommands.kt`, `SocketServer.kt`), acima do limite do `light` da issue.
- Bloqueio #33 já está fechado (custo por transcrição gravado como `RUN_COST_RECORDED`), então a fonte do custo existe.
- Exposição só pelo socket (`report.csv`); comando de CLI fica como follow-up para manter uma camada e o pipeline sem tocar `cli`.
- Estágio = coluna do quadro; custo por estágio fica fora (a issue pede lead time por estágio e custo por card).
- Custo desconhecido/ausente vira célula vazia + `cost_status`, nunca 0 (regra de produto de custo medido).
- Título neutralizado contra injeção de fórmula em CSV, porque o título vem de texto externo.
- Sem arquivo de teste novo: `RunCostTest.kt` cobre custo e agora o relatório (mesmo dado); `ProtocolTest.kt` cobre o pedido e a resposta do socket.

## Open questions
- none

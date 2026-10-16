# Relatório de lead time e custo por card — plano

**Issue:** #61 · **Spec:** `docs/superpowers/specs/2026-10-16-relatorio-de-lead-time-e-custo-por-card-design.md`

## Tarefas

| # | Camada | Agente | Dependência | Arquivos |
|---|---|---|---|---|
| 1 | daemon | techlead (solo) | — | `daemon/src/main/kotlin/sfv2/daemon/CardReport.kt` (novo: `CardReport.build`, `CardReport.toCsv`, `CardReportRow`, `CostStatus`) |
| 2 | daemon | techlead (solo) | 1 | `Protocol.kt` (`Request.ReportCsv`, `Response.ReportResult`), `CardCommands.kt` (resolve com `store.cards()`/`store.events(id)`), `SocketServer.kt` (roteia o request) |

Sem mudança em store, schema, config ou dependência.

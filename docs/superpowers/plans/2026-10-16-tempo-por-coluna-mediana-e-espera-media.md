# Tempo por coluna: mediana e espera média — plano

**Issue:** #231 · **Spec:** `docs/superpowers/specs/2026-10-16-tempo-por-coluna-mediana-e-espera-media-design.md`

## Tarefas

| # | Camada | Agente | Dependência | Arquivos |
|---|---|---|---|---|
| 1 | core | engineer | — | `core/src/main/kotlin/sfv2/core/ColumnTimes.kt` (novo: `ColumnTimes.of`, `ColumnTime`, `ColumnTimeKind`) |
| 2 | daemon | engineer | 1 | `daemon/src/main/kotlin/sfv2/daemon/ColumnTimesQuery.kt` (novo), `Protocol.kt` (`Request.ColumnTimes`, `Response.ColumnTimesResult`), `CardCommands.kt`, `SocketServer.kt` |
| 3 | app | engineer | 2 | `FeedSocket.kt` (`SocketColumnTimesSource`), `BoardMonitor.kt`, `BoardState.kt` (`ColumnTimeLabel`), `Theme.kt` (`Texts`), `BoardScreen.kt` |

Testes primeiro (QA): `core/src/test/kotlin/sfv2/core/ColumnTimesTest.kt` (novo), `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt`, `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`.

Sem mudança em store, schema, config ou dependência.

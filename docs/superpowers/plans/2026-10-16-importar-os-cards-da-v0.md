# Importar os cards da V0 — plano (#60)

Spec: `docs/superpowers/specs/2026-10-16-importar-os-cards-da-v0-design.md`

## Contrato (fixado aqui para QA e engenheiro)

- Request JSON: `{"cmd":"card.import_board","actor":"owner","project":"<opcional>","rows":[{"id":"12","column":"Technical Refinement"}],"proof":"<token>"}`. `Request.CardImportBoard(actor, project, rows: List<BoardRow>, proof)` com `BoardRow(id: String, column: String)`; `rows` ausente ou malformado → `Rejected`.
- Resposta: `Response.BoardImportResult(cards: List<CardView>, ignored: List<IgnoredRow>)`, `IgnoredRow(id, reason)` com reason `unknown_column` ou `not_found`. JSON: `{"ok":true,"result":[cards],"ignored":[{"id":"..","reason":".."}]}`.
- Tratado por `CardImport.handle` (mesma classe, mesma prova de dono); `cards` traz só os cards criados nesta chamada (linhas e busca de abertas); card já existente é pulado sem evento.
- Cards novos nascem direto na coluna mapeada, evento `CARD_CREATED` com `origin=v0`; colunas de Ready for Dev a Ready for Prod nascem em Spec Review com `v0_column=<nome original>` no payload. Issues abertas sem card nem linha: Inbox.

## Tarefas

1. **daemon** (engineer) — `Protocol.kt`, `CardImport.kt`, `SocketServer.kt`, `CardCommands.kt` (ramo "not a card command"). Depende do QA.
2. **cli** (engineer) — `CardCommands.kt`: `sfv2 card import-board <arquivo.tsv> [--project]`. Depende da tarefa 1 só pelo contrato acima.

Testes de aceitação: QA em `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt`.

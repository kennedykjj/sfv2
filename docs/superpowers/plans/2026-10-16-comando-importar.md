# Comando importar — plano

**Issue:** #54 · **Spec:** `docs/superpowers/specs/2026-10-16-comando-importar-design.md`

## Tarefas

1. **core** (agent: engineer) — `core/src/main/kotlin/sfv2/core/Event.kt`: adicionar `EventType.CARD_REIMPORTED`. Sem dependências.
2. **daemon** (agent: engineer) — depende da 1.
   - `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `Request.CardImport` (cmd `card.import`, `proof` mascarado no `toString`), parse.
   - `daemon/src/main/kotlin/sfv2/daemon/CardImport.kt` (novo): molde do `CardRebuild`; `normalizeTitle`, duplicata por projeto, reimportação, busca pulando fechadas.
   - `SocketServer.kt` (despacho), `CardCommands.kt` (ramo exaustivo), `ProjectDelivery.kt` (`trackerOf`), `Main.kt` (fiação).

Testes de aceitação (QA): `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt`.

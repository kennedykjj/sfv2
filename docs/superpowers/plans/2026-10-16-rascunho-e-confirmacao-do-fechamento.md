# Rascunho e confirmação do fechamento — plano (#56)

Spec: `docs/superpowers/specs/2026-10-16-rascunho-e-confirmacao-do-fechamento-design.md`

## Tarefas

### T1 — core: tipos de evento (agent: engineer, layer core)
- Arquivo: `core/src/main/kotlin/sfv2/core/Event.kt`.
- Adicionar ao `EventType`: `CLOSURE_CONFIRMED`, `CLOSURE_WRITTEN`, `CLOSURE_FAILED`.
- Depende de: nada.

### T2 — daemon: rascunho e confirmação (agent: engineer, layer daemon)
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/CardClosure.kt` (novo), `Protocol.kt`, `SocketServer.kt`, `CardCommands.kt`, `Main.kt`.
- `Protocol.kt`: `Request.CardCloseDraft(id)`, `Request.CardCloseConfirm(actor, id, draftHash, proof)` (toString mascara a prova), parse de `card.close.draft` / `card.close.confirm`, `Response.CloseDraftResult(status, summary, prLink, draftHash)` e seu `render`.
- `CardClosure`: formato do `CardImport` (store, recorder, registry, trackerOf, ownerProof, clock, log); regras da seção Architecture da spec (rascunho sem título, link do PR da url da referência, `no_pr_recorded`, hash SHA-256, `CLOSURE_CONFIRMED` antes de `tracker.close`, `CLOSURE_WRITTEN`/`CLOSURE_FAILED`, retry sem novo evento).
- `SocketServer`/`Main`: roteamento e wiring; `CardCommands`: "not a card command" para os dois novos.
- Depende de: T1.

## Testes
Aceitação: `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` (QA, antes das tarefas).

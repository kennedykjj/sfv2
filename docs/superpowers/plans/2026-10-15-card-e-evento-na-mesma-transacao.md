# Card e evento na mesma transação — plano

**Issue:** #13 · **Spec:** `docs/superpowers/specs/2026-10-15-card-e-evento-na-mesma-transacao-design.md` · **Peso:** light

## Tarefas

### T1 — Queries nomeadas do store (camada `store`)
- Arquivo: `store/src/main/sqldelight/sfv2/store/db/Schema.sq`
- Adicionar queries nomeadas: upsert de `card` (por `id`), insert de `event`, select de `event` por `card_id` (ordem de inserção), select de `card` por `id`.
- Esquema v1 não muda; nenhuma migração.
- Dono: `engineer` (camada `store`).

### T2 — `CardEventStore` (camada `store`)
- Arquivo novo: `store/src/main/kotlin/sfv2/store/CardEventStore.kt`
- `class CardEventStore(database: Database)`:
  - `fun recordChange(cardId: String, card: Card, event: Event)`: grava card (upsert) e insere evento dentro de uma única `transaction {}`; exceção original propaga e a transação reverte.
  - `fun events(cardId: String): List<Event>` (somente leitura, decodifica com `EventJson`).
  - `fun card(cardId: String): Card?` (somente leitura, decodifica com `EventJson`).
- Sem nenhum método de update/delete de evento; sem outra via de escrita de card.
- Dependências: T1 (queries nomeadas), `core` (`Card`, `Event`, `EventJson`).
- Dono: `engineer` (camada `store`).

### T3 — Testes de aceitação (QA, fora deste plano)
- Testes em `store/src/test/kotlin/sfv2/store/ModuleTest.kt`, um por AC (AC-1 a AC-5), escritos pelo `qa-engineer` antes da implementação.

## Ordem e dependências

T1 → T2. Testes (T3) antes de T1/T2.

## Arquivos tocados

- `store/src/main/sqldelight/sfv2/store/db/Schema.sq` (alterado)
- `store/src/main/kotlin/sfv2/store/CardEventStore.kt` (novo)
- `store/src/test/kotlin/sfv2/store/ModuleTest.kt` (QA)

## Fora do plano

Migração (nenhuma), camada `core`, `ports`, `adapters`, `daemon`, `cli`, `app`, `e2e`.

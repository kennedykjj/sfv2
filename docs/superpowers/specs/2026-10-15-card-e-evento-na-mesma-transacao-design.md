# Card e evento na mesma transação — spec

**Issue:** #13 · **Branch:** `feat/13-card-e-evento-na-mesma-transacao` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** um card que muda sem evento quebra a auditoria; um evento sem mudança mente. O histórico de eventos só vale como trilha se for exatamente o espelho do estado do card e não puder ser reescrito.
**Audience:** o desenvolvedor dentro de uma empresa, que precisa de trilha de auditoria confiável e assina as decisões (também serve ao desenvolvedor solo, que confia no histórico do card).
**Acceptance criteria**
- **AC-1** — Dado uma mudança de card, quando ela é gravada, então o card atualizado e o evento correspondente ficam gravados juntos, na mesma transação.
- **AC-2** — Dado uma falha na gravação do evento, quando a mudança do card é tentada, então o card permanece como estava e nenhum evento novo existe.
- **AC-3** — Dado uma falha na gravação do card, quando a mudança é tentada, então nenhum evento novo existe e o card permanece como estava.
- **AC-4** — Dado um evento já gravado, quando se consulta o store depois de qualquer mudança posterior, então o evento está idêntico ao original.
- **AC-5** — Dado a API pública do store, quando se procura uma operação para alterar ou apagar um evento, então ela não existe.
**Out of scope:** definição dos tipos de evento e do ator (outras issues); consulta, paginação ou exibição do histórico; assinatura do dono; retenção ou expurgo de eventos; mudança de card sem evento por qualquer via (não há operação para isso).
**Product fit:** serve ao princípio "toda ação é um evento com ator" e à trilha de auditoria da fábrica dona do próprio estado; não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos (módulo `store`, pacote `sfv2.store`):**
- `class CardEventStore(database: Database)` com `fun recordChange(cardId: String, card: Card, event: Event)`: grava (upsert) o card e insere o evento numa única `transaction {}` do SQLDelight. Não existe outra via de escrita de card nem qualquer método de update/delete de evento.
- `fun events(cardId: String): List<Event>` e `fun card(cardId: String): Card?` só de leitura (necessárias para verificar os efeitos; decodificam com `EventJson`).
- Falha em qualquer escrita lança a exceção original e a transação inteira é revertida (nada parcial).

**Camadas e módulos:** só `store` (usa `core`: `Card`, `Event`, `EventJson`). `Database` e o esquema v1 não mudam: `card(id, title, column_name)` e `event(id PK, card_id, at, actor, type, payload_json)`; `payload_json` guarda o JSON completo do `EventJson.encode`. Referências externas do card ficam fora desta issue.

**Arquivos:** `store/src/main/kotlin/sfv2/store/CardEventStore.kt` (novo), `store/src/main/sqldelight/sfv2/store/db/Schema.sq` (queries nomeadas de upsert/insert/select), `store/src/test/kotlin/sfv2/store/ModuleTest.kt`.

**Imutabilidade do evento:** garantida pela API (sem operação de update/delete) e pela PK de `event.id` (reinserir o mesmo id falha e reverte o card). Gatilhos no banco exigiriam migração 2; fora do escopo.

**Migração:** nenhuma (esquema v1 inalterado).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-5 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Falhas nos testes (AC-2/AC-3) são provocadas sem mock: evento com id duplicado (PK) faz a escrita do evento falhar; para a falha do card, o teste cria via JDBC um gatilho `BEFORE INSERT/UPDATE ON card` com `RAISE(ABORT)` no banco temporário.
- Imutabilidade só pela API e PK, sem gatilhos: gatilho exigiria migração 2 e a issue pede apenas a API (AC-3 original).
- Testes na suíte existente `ModuleTest.kt`, que cobre o módulo; sem arquivo novo.
- Id do card é `String` no store: o `core` ainda não tem `CardId`; criar um tipo é outra issue.
- Peso `light` pela regra (uma camada, ≤3 arquivos, sem segurança), apesar do "standard" esperado na issue; 5 ACs no total.

## Open questions
- none

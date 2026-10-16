# Store: persistir referências externas do card (follow-up do #13) — spec

**Issue:** #111 · **Branch:** `feat/111-store-persistir-referencias-externas-do-card` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o `CardEventStore` grava só título e coluna; as referências externas (`ExternalRef`) somem ao reler o card, sem erro. O vínculo do card com o Jira/GitHub de origem se perde.
**Público:** o desenvolvedor dentro de uma empresa (Jira, trilha de auditoria), que precisa que o card importado continue apontando para o ticket de origem.

**Critérios de aceitação**
- AC-1: Dado um card com referências externas, quando é gravado e lido de volta, então as referências voltam iguais (provedor, id externo, url, data de importação, hash do texto).
- AC-2: Dado um card sem referências externas, quando é gravado e lido de volta, então volta com lista vazia.
- AC-3: Dado um banco na versão anterior com cards e eventos, quando o store abre, então migra sem perder cards nem eventos, e os cards antigos voltam sem referências.
- AC-4: Dado um card que já tem uma referência com certo provedor e id externo, quando se grava outra com o mesmo provedor e id no mesmo card, então o banco recusa a segunda e o card permanece como estava.
- AC-5: Dado o mesmo provedor e id externo em cards diferentes, quando ambos são gravados, então os dois são aceitos.

**Fora do escopo:** chamadas a provedores; aviso de texto alterado (fase 4); sincronização contínua com o tracker.

**Aderência ao produto:** serve o princípio "a fábrica é dona do próprio estado": o card guarda a origem localmente, sem tocar o provedor. Não contradiz o posicionamento.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** `CardEventStore.recordChange`, `card` e `cards` passam a gravar e ler `Card.references` (ordem preservada). Nenhuma assinatura pública muda; `core` não muda.
- **Camada:** só `store`. Arquivos: `store/src/main/sqldelight/sfv2/store/db/Schema.sq`, `store/src/main/sqldelight/migrations/3.sqm` (novo), `store/src/main/sqldelight/databases/3.db` (snapshot gerado pelo SQLDelight), `store/src/main/kotlin/sfv2/store/CardEventStore.kt`, `store/src/main/kotlin/sfv2/store/Database.kt` (`SCHEMA_VERSION` 3 → 4), `store/src/test/kotlin/sfv2/store/ModuleTest.kt`.
- **Esquema:** tabela `card_reference(card_id REFERENCES card(id), position INTEGER, provider, external_id, url, imported_at, text_hash)` com `PRIMARY KEY (card_id, provider, external_id)`. `imported_at` em ISO-8601 (`Instant.toString()`), `text_hash` com o `TextHash.value`.
- **Gravação:** em `recordChange`, dentro da mesma transação do card e do evento, apaga as referências do card e reinsere na ordem da lista (`position`). Falha em qualquer escrita desfaz tudo.
- **Leitura:** `card` e `cards` montam o `Card` reaplicando `withReference` na ordem de `position`.
- **Migração (expand):** `3.sqm` só cria a tabela e não altera dados; cards antigos voltam sem referências. Aditiva, sem passo destrutivo.

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
- Tabela filha `card_reference` em vez de coluna JSON no `card`: a unicidade (provedor, id externo) por card vira constraint do banco, como pede o AC-4.
- Apagar e reinserir as referências a cada `recordChange`: o `Card` é o estado inteiro, então o banco espelha a lista sem lógica de diff.
- `Card` já recusa duplicata em `withReference`; a recusa do banco (AC-4) é a segunda barreira e o teste a exercita por inserção direta via JDBC.
- Testes entram no `ModuleTest.kt` existente, que cobre o módulo; os testes que afirmam a versão 3 passam a afirmar a 4.
- Bloqueio do #110 conferido: já fechado, o banco está na versão 3.

## Open questions
- none

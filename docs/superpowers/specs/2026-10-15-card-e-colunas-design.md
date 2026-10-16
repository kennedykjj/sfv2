# Card e colunas — spec

**Issue:** #8 · **Branch:** `feat/8-card-e-colunas` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problema:** o núcleo precisa de um modelo de card e de colunas independente de banco e de GitHub, para que o estado da fábrica viva na máquina.
**Audiência:** o desenvolvedor sozinho com muitos projetos (e, por extensão, o desenvolvedor em empresa); consumidor direto são os módulos do próprio núcleo.

**Critérios de aceitação**
- AC-1: Dado um card criado sem coluna informada, quando ele é criado, então sua coluna é Inbox.
- AC-2: Dado o conjunto de colunas, quando listado, então a ordem é Inbox, Ready for Refinement, Refinement, Spec Review, Ready for Dev, Coding, Validation, Staged, Ready for Prod, Done.
- AC-3: Dado um título vazio ou só com espaços, quando o card é criado, então a criação falha com erro de validação que identifica o campo título.
- AC-4: Dado um título válido com espaços nas pontas, quando o card é criado, então o título do card não tem esses espaços.

**Fora de escopo:** persistência; regras de movimento entre colunas; integração com GitHub, Jira ou qualquer tracker.

**Aderência ao produto:** serve ao princípio "a fábrica é dona do próprio estado" (card independente de banco e de tracker) e à lista de colunas em Capabilities. Não contradiz posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Módulo `core` apenas, funções puras, sem IO.
- `core/src/main/kotlin/sfv2/core/Column.kt`: `enum class Column` com as 10 colunas na ordem do AC-2 (`entries` dá a ordem); `displayName` com o nome exibido (ex.: "Ready for Refinement").
- `core/src/main/kotlin/sfv2/core/Card.kt`: `data class Card(val title: String, val column: Column = Column.INBOX)` imutável; construção por `Card.create(title: String, column: Column? = null): Card`, que apara o título e lança `CardValidationException` (estende `RuntimeException`, mensagem cita o campo `title`) se vazio após o aparo. O construtor primário é `private` para impedir card inválido.
- Sem id nem timestamps: ficam para os cards de persistência e eventos.
- Contratos externos: nenhum (sem endpoint, evento ou migração).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso light: uma camada (core), 4 ACs, 3 arquivos, sem UI nem segurança.
- AC-4 (aparar o título), acrescentado pelo PO, mantido: baixo custo, evita títulos com espaços nas pontas.
- Coluna "Refinement" segue o AC e o PRODUCT.md; o board do GitHub usa "Technical Refinement", e o mapeamento fica para o tracker/adapter.
- Arquivo de teste novo `CardTest.kt`: o único existente (`ModuleTest.kt`) cobre só o nome do módulo, não o domínio.
- Sem id/timestamp no card: fora do escopo (persistência), entram no card de eventos/store.

## Open questions
- none

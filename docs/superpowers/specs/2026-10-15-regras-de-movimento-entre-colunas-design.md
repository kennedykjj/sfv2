# Regras de movimento entre colunas — spec

**Issue:** #10 · **Branch:** `feat/10-regras-de-movimento-entre-colunas` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** quem pode mover um card de uma coluna para outra é regra de produto, e na V0 ela está espalhada entre dashboard e dispatcher. Sem um lugar único, a fábrica pode acabar aprovando sozinha o que é decisão do dono.
**Audience:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa: ambos precisam ter certeza de que nada passa de Spec Review nem sai de Ready for Prod sem a aprovação dele.
**Acceptance criteria**
- **AC-1** — Dado um card em Spec Review, quando a fábrica (ator agente) tenta movê-lo para Ready for Dev, então o movimento é recusado com um motivo que diz que só o dono aprova.
- **AC-2** — Dado um card em Spec Review, quando o dono (ator dono) aprova, então o movimento para Ready for Dev é aceito.
- **AC-3** — Dado um card em Ready for Prod, quando a fábrica tenta movê-lo para Done, então é recusado; quando o dono libera, é aceito.
- **AC-4** — Dado qualquer par (coluna de origem, coluna de destino) e qualquer ator, quando se consulta a regra, então a resposta é permitido ou recusado (nunca erro), e pares fora da tabela são recusados.
- **AC-5** — Dado a consulta de regras, quando ela roda duas vezes com a mesma entrada, então o resultado é idêntico (função pura, sem IO nem relógio).
- **AC-6** — Dado a tabela de movimentos permitidos, então ela existe num único lugar do código e um teste percorre todos os pares de colunas e atores contra ela.
**Out of scope:** persistência; registrar o evento do movimento; movimentos além dos que PRODUCT.md e ARCHITECTURE.md sustentam (a tabela M1-M7 da V0 não está disponível e não é inventada aqui); gates que movem o card (outra issue); UI.
**Product fit:** serve ao princípio "toda ação é um evento com ator" e à restrição de que as decisões do dono são Spec Review e Ready for Prod; sustenta "não é um agente que decide sozinho o que vai para produção". Não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contrato novo (`core`, pacote `sfv2.core`), arquivo `core/src/main/kotlin/sfv2/core/MoveRules.kt`:**
  - `enum class MoveAuthority { OWNER_ONLY, FACTORY_OR_OWNER }`.
  - `sealed interface MoveDecision { data object Allowed; data class Refused(val reason: String) }`.
  - `object MoveRules { val table: Map<Pair<Column, Column>, MoveAuthority>; fun check(from: Column, to: Column, actor: Actor): MoveDecision }`.
- `check` é pura: consulta `table`; par ausente → `Refused("move from X to Y is not allowed")`; `OWNER_ONLY` com ator diferente de `Actor.Owner` → `Refused` com motivo "only the owner approves ...". `Actor.Owner` pode qualquer movimento da tabela.
- Tabela (única fonte): Inbox→Ready for Refinement (dono); Ready for Refinement→Refinement, Refinement→Spec Review (fábrica); Spec Review→Ready for Dev (dono); Spec Review→Ready for Refinement (dono, retrabalho); Ready for Dev→Coding, Coding→Validation, Validation→Coding, Validation→Staged (fábrica); Staged→Ready for Prod (fábrica); Staged→Coding (fábrica, correção); Ready for Prod→Done (dono).
- Camadas/módulos: só `core`. Sem migração, sem endpoint, sem evento (registrar o movimento fica fora do escopo).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |
| AC-5 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |
| AC-6 | core | `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo `MoveRulesTest.kt`: nenhuma suíte existente (CardTest, EventTest, ModuleTest) cobre regras de movimento.
- Só duas decisões do dono vêm do produto (Spec Review→Ready for Dev e Ready for Prod→Done); o resto da tabela é proposto a partir do fluxo de colunas do PRODUCT.md, porque a tabela M1–M7 da V0 não está acessível à fábrica.
- Autoridade em dois níveis (`OWNER_ONLY`, `FACTORY_OR_OWNER`): o dono pode tudo que está na tabela, a fábrica só o que não é do dono; par ausente é recusado (default seguro).
- Resultado como `MoveDecision` sealed (não booleano nem exceção): recusa é caso normal e carrega o motivo.
- Movimento de retrabalho (Spec Review→Ready for Refinement) é do dono, pois ele que rejeita a spec.

## Open questions
- **Tabela M1–M7:** A (recomendado) aceitar a tabela proposta em Architecture ou B conferir contra o DESIGN.md da V0 antes do desenvolvimento — B evita divergência, mas atrasa o card.

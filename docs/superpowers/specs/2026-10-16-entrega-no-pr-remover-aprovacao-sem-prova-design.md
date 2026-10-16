# Entrega no PR: remover aprovação que fabrica ator dono sem prova — spec

**Issue:** #178 · **Branch:** `feat/178-entrega-no-pr-remover-aprovacao-sem-prova` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problema:** `PullRequestHandoff.approve(cardId)` grava `CARD_MOVED` Staged → Ready for Prod com `Actor.Owner` sem prova. Hoje ninguém o chama, mas é API pública que contorna a regra "ator dono exige prova" (#115). A aprovação real já é o `card.move` do dono, com prova.

**Audiência:** o desenvolvedor dentro de uma empresa (trilha de auditoria, entrega no PR), que precisa confiar que todo evento do dono é assinado.

**Critérios de aceitação**
- AC-1: Dado o `PullRequestHandoff`, quando se inspeciona sua API pública, então nenhum método grava evento com `Actor.Owner`; só `reconcile` permanece.
- AC-2: Dado um card Staged em modo PR, quando o dono o move para Ready for Prod via `card.move` com prova, então o card chega a Ready for Prod com evento do dono assinado.
- AC-3: Dado um card em modo PR, quando `reconcile` roda (qualquer estado do PR), então `Forge.merge` nunca é chamado.
- AC-4: Dado os testes do AC-1 do #47, então verificam o modo PR pela coluna Ready for Prod criada por `card.move` do dono, e não por `approve`.

**Fora de escopo:** mudar `reconcile`, o fluxo de `card.move` ou a regra de prova do #115; qualquer outro modo de entrega.

**Aderência ao produto:** serve "toda ação é um evento com ator; a aprovação do dono é um evento assinado" e "não é um agente que decide sozinho o que vai para produção". Não contradiz posicionamento nem escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contrato:** `PullRequestHandoff` (`daemon/src/main/kotlin/sfv2/daemon/PullRequestHandoff.kt`) perde o método público `approve(cardId)`; só `reconcile` fica. Nenhum endpoint ou evento muda.
- **`PullRequestHandoff.kt`:** remover `approve`; o `move(...)` privado segue servindo `reconcile` (só com `Actor.Factory`). Ajustar o KDoc da classe e o de `HandoffOutcome.Waiting` ("or the approval was just recorded" some). A entrada `handoff_approved` do log some junto.
- **Chamadores de produção:** nenhum (`AutonomousDelivery` e `RefineRuntime` só usam `reconcile`).
- **Testes:** `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt`; o teste do AC-1 do #47 passa a usar o helper `ownerApproves` (evento de `card.move` do dono: só `from` e `to`).
- **Camadas:** só `daemon`. Sem migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- AC-1 é verificado por reflexão Java: `PullRequestHandoff::class.java.declaredMethods` públicos não incluem `approve` e `reconcile` existe.
- AC-2 usa o helper `ownerApproves` (evento do dono como o board grava), não o handler real de `card.move`: a prova do dono é coberta pelos testes do #115; aqui só se afirma que o fluxo funciona sem `approve`.
- Peso `light`: uma camada, 1 arquivo de produção e 1 de teste, 4 ACs.
- Sem arquivo de teste novo: `PullRequestHandoffTest` já cobre o módulo.

## Open questions
- none

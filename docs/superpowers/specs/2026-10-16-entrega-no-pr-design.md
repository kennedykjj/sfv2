# Entrega no PR — spec

**Issue:** #47 · **Branch:** `feat/47-entrega-no-pr` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** No modo de entrega no PR (uso em empresa), a fábrica não pode mergear: o PR precisa de revisão e merge humanos. Hoje o card não tem onde esperar essa revisão nem reage ao desfecho do PR.
**Audience:** O desenvolvedor dentro de uma empresa (revisores obrigatórios, trilha de auditoria).
**Acceptance criteria**
- **AC-1** — Given um projeto em modo entrega no PR e um card em Staged, when o dono aprova a entrega, then o PR fica aberto sem merge da fábrica e o card espera em Ready for Prod (a coluna faz o papel de revisão humana; o board não tem coluna própria).
- **AC-2** — Given um card esperando em Ready for Prod com PR aberto, when outra pessoa mergeia o PR, then o card vai a Done.
- **AC-3** — Given um card esperando em Ready for Prod com PR aberto, when o PR é fechado sem merge, then o card volta a Staged com o motivo do fechamento registrado e visível ao dono.
**Out of scope:** Modo autônomo; abrir o PR e esperar o CI (já feito na validação); sincronização contínua com o forge além de observar o desfecho do PR; reabrir ou reenviar o PR automaticamente; coluna nova no board.
**Product fit:** Serve "o dono decide onde a decisão é dele" e "não é um agente que decide sozinho o que vai para produção"; implementa o modo de entrega por projeto. Sem contradição com posicionamento nem com o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** `MoveRules.table` ganha `(READY_FOR_PROD to STAGED) to FACTORY_OR_OWNER` (core). Novo `PullRequestHandoff` no `daemon`, com `approve(cardId)` e `reconcile(cardId): HandoffOutcome` (`Waiting`, `Merged`, `ClosedUnmerged`, `ForgeFailed`). Usa `Forge.findOpenPullRequest` e `Forge.state` (já existem em `ports`); sem mudança de interface.
- **Aprovação (AC-1):** com `policies.deliveryMode == PR_HANDOFF`, a aprovação do dono em Staged move o card para Ready for Prod e nunca chama `Forge.merge`. O número do PR vai no payload do evento `CARD_MOVED` dessa aprovação (chave `pr`), para o `reconcile` achar o PR depois de ele sair da lista de abertos.
- **Desfecho (AC-2, AC-3):** `reconcile` lê `Forge.state` do PR registrado. `Merged` → move para Done (ator fábrica). `ClosedUnmerged` → move para Staged (ator fábrica) com payload `reason=pr_closed_unmerged`, visível ao dono. `Open` ou `ForgeException` → o card fica onde está e o próximo ciclo tenta de novo. O estado do forge é dado: nunca vira instrução.
- **Disparo:** o `Dispatcher` chama `reconcile` a cada ciclo para cada card em Ready for Prod de projeto em modo PR; o modo autônomo não é tocado (S3-04/#46).
- **Camadas:** `core` (`MoveRules.kt`) e `daemon` (`PullRequestHandoff.kt`, fiação em `Dispatcher.kt`). Sem migração, sem mudança de `store`.
- **Atualizar `ARCHITECTURE.md`/`PRODUCT.md`:** não; a fronteira entre camadas não muda.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Sem coluna "Revisão humana": as 10 colunas são fixas (PRODUCT.md); Ready for Prod faz o papel no modo PR.
- Retorno por PR fechado vai a Staged, a coluna de decisão do dono; exige o movimento novo Ready for Prod → Staged em `MoveRules`.
- Arquivo de teste novo `PullRequestHandoffTest.kt`: nenhuma suíte do daemon cobre a entrega no PR (`ValidatePipelineTest` cobre só o estágio validate). O movimento novo em `MoveRulesTest` (core) é coberto no mesmo PR como teste de unidade, fora das ACs.
- Peso `heavy` (a issue sugeria `standard`): a spec toca duas camadas (`core` e `daemon`).

## Open questions
- none

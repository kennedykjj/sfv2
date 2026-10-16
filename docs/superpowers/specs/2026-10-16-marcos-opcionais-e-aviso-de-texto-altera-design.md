# Marcos opcionais e aviso de texto alterado — spec

**Issue:** #55 · **Branch:** `feat/55-marcos-opcionais-e-aviso-de-texto-altera` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o dono quer visibilidade no quadro da empresa (Jira/GitHub) sem sincronização contínua. Hoje a fábrica só toca o tracker na importação e no fechamento; faltam marcos opcionais no meio do caminho, e o dono não é avisado se o texto externo mudou depois de importado.

**Audiência:** o desenvolvedor dentro de uma empresa (quadro do time precisa refletir o andamento).

**Critérios de aceitação**
- AC-1: Dado o marco `started` ligado na política do projeto, quando o card entra em Coding, então o status externo da issue muda uma única vez, mesmo que o gatilho se repita.
- AC-2: Dado o marco `pr_opened` ligado, quando o PR do card é aberto, então o link do PR é escrito no tracker uma única vez.
- AC-3: Dado que o hash do texto externo difere do hash importado, quando o card é reimportado, então o dono recebe um evento de aviso.
- AC-4: Dado um marco desligado (padrão), quando o gatilho ocorre, então nada é escrito no tracker.
- AC-5: Dado que o tracker falha ao escrever um marco, quando o gatilho ocorre, então o card segue sem bloqueio, o marco não é dado como escrito e é tentado de novo no próximo gatilho.
- AC-6: Dada a política do projeto, então ela aceita a lista de marcos ligados (`started`, `pr_opened`); sem a lista, todos ficam desligados.
- AC-7: Dado um hash novo diferente, quando ocorre a reimportação ou antes de escrever um marco, então o aviso de texto alterado é emitido uma vez por hash novo, e nunca contém o texto externo (só ids e hashes).

**Fora de escopo:** fechamento (#56), porta única de texto (#57), CLI, UI, sincronização contínua.

**Product fit:** serve ao princípio "o tracker externo é tocado só na importação, em marcos opcionais e no fechamento"; marcos desligados por padrão respeitam "nada sai da máquina sem política explícita do projeto". Não contradiz o posicionamento.

**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** `core` (política e catálogo de eventos) e `daemon` (escrita dos marcos e aviso). `Tracker.writeMilestone(id, milestone: String)` já existe em `ports`; nada muda lá.

**Contratos**
- `core/ProjectPolicies.kt`: novo `enum class Milestone(val key: String) { STARTED("started"), PR_OPENED("pr_opened") }` e campo `milestones: Set<Milestone>` em `ProjectPolicies` (padrão: vazio). `from(raw)` lê a chave `milestones` (lista de strings); valor que não é lista, ou chave desconhecida, lança `InvalidPolicyException("milestones", ...)`.
- `core/Event.kt`: dois `EventType` novos, `MILESTONE_WRITTEN` (payload `milestone`, `externalId`) e `EXTERNAL_TEXT_CHANGED` (payload `externalId`, `previousHash`, `newHash`; nunca texto).
- `daemon/ExternalMilestones.kt` (novo): `class ExternalMilestones(store, recorder, trackerOf, policiesOf, clock, log)` com `fun reached(cardId: String, milestone: Milestone, detail: String? = null)`. Passos: marco fora de `policies.milestones` → retorna; já existe `MILESTONE_WRITTEN` do mesmo `milestone` no card → retorna (uma vez); card sem referência `github` → retorna; `tracker.fetch` e, se o hash difere da referência, registra `EXTERNAL_TEXT_CHANGED` (uma vez por `newHash`); `tracker.writeMilestone`; registra `MILESTONE_WRITTEN` (ator `Actor.Adapter("github")`) só depois do sucesso. `TrackerException` é capturada: log `milestone_failed card=<id> milestone=<key>`, sem evento, sem propagar.
- Texto do marco: `started` → `started`; `pr_opened` → link do PR `<base da url da referência>/pull/<número>`, onde a base é a url da referência sem o sufixo `/issues/<id>` (o `Forge` não expõe a url do PR).
- `daemon/CardImport.kt`: ao reimportar com hash diferente, além do `CARD_REIMPORTED`, registra `EXTERNAL_TEXT_CHANGED` (mesma regra de uma vez por `newHash`; a lógica fica numa função compartilhada com `ExternalMilestones`).
- Gatilhos: `daemon/DevelopPipeline.kt` chama `reached(cardId, STARTED)` após a movimentação Ready for Dev → Coding; `daemon/ValidatePipeline.kt` chama `reached(cardId, PR_OPENED, "<número>")` quando `deliver` devolve `CiResult.Green`; `daemon/Main.kt` monta `ExternalMilestones` com o `trackerOf` e as políticas do projeto do card.

**Migração:** nenhuma; evento novo é aditivo e a política sem `milestones` mantém o comportamento atual.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-6 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Layers: o card diz `daemon`, mas o interruptor por marco exige campo novo na política (`core`); sem ele "marco ligado" não tem onde viver. Peso sobe para heavy (2 camadas).
- Ligar um marco é listá-lo em `milestones`; `signers.milestone` não é consultado, porque a política é versionada e editada pelo dono (a assinatura dele já está na lista).
- Marcos desligados por padrão: a fábrica não escreve em sistema externo sem política explícita (PRODUCT.md).
- "Uma vez" vem do evento `MILESTONE_WRITTEN` no próprio card, não de estado em memória: sobrevive a reinício.
- Aviso de texto alterado dispara na reimportação e antes de escrever marco; sem polling, para respeitar "sem sincronização contínua" (decisão 5).
- O aviso é um evento no stream (o dono o vê por lá); canal de notificação fica para um card próprio.
- Link do PR derivado da url da referência externa, porque `PullRequestRef` não tem url; só vale para GitHub, único tracker existente.
- Arquivo de teste novo `ExternalMilestonesTest.kt`: nenhuma suíte do daemon cobre escrita de marcos.

## Open questions
- none

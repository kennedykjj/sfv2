# Entrega autônoma — spec

**Issue:** #46 · **Branch:** `feat/46-entrega-autonoma` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Num projeto em modo de entrega autônomo, depois que o dono aprova em Ready for Prod, ninguém mergeia o PR: o card fica parado. A fábrica precisa mergear sozinha, só o que o dono aprovou, e fechar o card apenas quando a main estiver verde.
**Audience:** O desenvolvedor sozinho com muitos projetos (pessoais e vibe-coded), que usa o modo de entrega autônomo.
**Acceptance criteria**
- **AC-1** — Dado um projeto em modo autônomo e um card que o dono moveu de Staged para Ready for Prod, quando a fábrica mergeia, então o merge só acontece se o head atual do PR for igual ao `head=<sha>` registrado no evento Staged (o aprovado).
- **AC-2** — Dado que o head do PR é diferente do aprovado, quando a fábrica tenta mergear, então nenhum merge ocorre e o card volta ao dono (Staged) com um evento que diz o motivo e os dois shas.
- **AC-3** — Dado que o merge tem conflito, ou o PR não está mais aberto, ou o forge falha, então o card não vai a Done, nenhum merge é dado como feito e o card volta ao dono com o motivo registrado.
- **AC-4** — Dado um merge feito, quando a fábrica acompanha o CI da main, então o card só vai a Done com o CI da main verde.
- **AC-5** — Dado um merge feito e o CI da main vermelho ou sem resultado dentro do prazo, então o card não vai a Done e volta ao dono com o motivo.
- **AC-6** — Dado um projeto em modo entrega no PR, quando o dono aprova em Ready for Prod, então a fábrica não mergeia nem toca o PR.
- **AC-8** — Dado um card que já voltou ao dono uma vez (Staged por `main_ci_red`, `head_mismatch` ou `forge_failed`) e que o dono aprovou de novo, quando a fábrica mergeia, então usa o `head` do Staged gravado pelo validate e ignora o evento de retorno; e aprovar de novo um card cujo merge já foi gravado só reacompanha o CI da main (AC-7).
- **AC-7** — Dado que a fábrica já mergeou um card, quando o passo roda de novo (reinício do daemon, reentrega), então não há segundo merge nem evento de merge duplicado.
**Out of scope:** Modo entrega no PR; rollback ou revert automático se o CI da main falhar; resolução automática de conflito; a aprovação em si (Staged para Ready for Prod, evento do dono); fechamento do item no tracker externo; qualquer UI.
**Product fit:** Serve "o dono aparece só onde a decisão é dele": a aprovação em Ready for Prod é o evento assinado que dispensa nova pergunta, e o head aprovado impede mergear código que o dono não viu. "Gate verde, não relato de agente": Done só com CI da main verde. Não contradiz o posicionamento; "não é um agente que decide sozinho o que vai para produção" é respeitado porque a decisão continua do dono e o modo autônomo é política explícita do projeto.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camadas: `core` (regra de movimento e tipo de evento) e `daemon` (a entrega). Contratos de `ports` (`Forge.merge`, `checks`, `state`) e `FakeForge` já existem e não mudam.

- `core/.../MoveRules.kt`: nova linha `(READY_FOR_PROD to STAGED) to FACTORY_OR_OWNER` (o "volta ao dono"). `core/.../Event.kt`: novo `EventType.PR_MERGED` (payload `pr`, `merge_commit`, `head`), o registro durável do merge para a idempotência.
- `daemon/.../AutonomousDelivery.kt` (novo): `class AutonomousDelivery(store, recorder, forge: (cardId) -> Forge, policies: (cardId) -> ProjectPolicies, pause: CiPause, maxPolls: Int, clock, log)` com `fun deliver(cardId): DeliveryOutcome`; sealed `DeliveryOutcome`: `Done`, `ReturnedToOwner(reason)`, `Skipped`.
  1. `Skipped` se o card não está em Ready for Prod ou se `deliveryMode != AUTONOMOUS` (modo PR nunca toca o PR, AC-6).
  2. `approvedHead` = `head` do último `CARD_MOVED` com `to=STAGED` **gravado pelo validate** (ator `Factory("validate")`, payload `ci=green` e `head`, o que o #45 grava). Os retornos ao dono feitos por este passo (`Factory("deliver")`, sem `head`) são ignorados na busca: sem isso, depois de uma volta ao dono o card nunca mais acharia o head aprovado (`no_approved_head` para sempre). Sem nenhum evento assim → volta ao dono com `no_approved_head`.
  3. Se já existe `PR_MERGED` do card, não chama `merge` (AC-7) e segue para o passo 5 com o `merge_commit` registrado. Senão: `forge.findOpenPullRequest(branch)`; sem PR aberto → `pr_not_open`.
  4. `forge.merge(pr, approvedHead)`: `Merged(c)` → grava `PR_MERGED` e segue; `HeadMismatch(actual)` → volta ao dono com `head_mismatch expected=<sha> actual=<sha>` (AC-2); `Conflict` → `merge_conflict`; `NotOpen` → `pr_not_open`; `ForgeException` → `forge_failed` (AC-3). O merge nunca é repetido após erro de rede incerto: a próxima entrega reconsulta `forge.state(pr)`; `Merged` sem `PR_MERGED` gravado → volta ao dono com `merge_unrecorded` (não há como saber o commit sem adivinhar).
  5. Acompanha a main: polling de `forge.checks(mergeCommit)` até sair de `Pending` ou estourar `maxPolls` (padrão 40, `CiPause` de 30 s, os mesmos de `PullRequestDelivery`). `Passed` → `CARD_MOVED` READY_FOR_PROD→DONE (ator `Factory("deliver")`, payload `merge_commit`, `ci=green`) (AC-4). `Failed` → volta ao dono com `main_ci_red` e os nomes dos checks (resumo cortado a 2048 bytes UTF-8, dado não confiável); `Pending` esgotado → `main_ci_timeout` (AC-5). Não há revert (fora de escopo).
  - "Volta ao dono" = `CARD_MOVED` READY_FOR_PROD→STAGED (ator `Factory("deliver")`) com `reason` (≤ 2048 bytes) e os dados do motivo. Nunca loga corpo de PR nem log de CI, só ids/shas.
- `daemon/.../Dispatcher.kt`: parâmetro opcional `deliveries: DeliveryTrigger` (`fun interface`, `fun trigger(cardId: String)`); em `runCycle`, depois das checagens de pausa/horário/limite diário, chama `trigger` para cada card em Ready for Prod. A implementação de produção (`AutonomousDeliveryTrigger`) roda `deliver` num executor de uma thread, com conjunto de cards em andamento (sem entrega concorrente do mesmo card), para o polling do CI não travar o ciclo; fecha com o daemon.
- `daemon/.../RefineRuntime.kt`: monta `AutonomousDelivery` com `ProjectDelivery.forgeOf(project)` (extrai a criação do `Forge` hoje embutida em `ProjectDelivery.create`) e passa o trigger ao `Dispatcher`.
- Sem migração de banco. Arquivos: `MoveRules.kt`, `Event.kt`, `AutonomousDelivery.kt`, `Dispatcher.kt`, `ProjectDelivery.kt`, `RefineRuntime.kt`, `MoveRulesTest.kt`, `AutonomousDeliveryTest.kt`, `DispatcherTest.kt`.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |

Também cobrem a regra nova de movimento (Ready for Prod → Staged pela fábrica) as linhas de `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt`, que acompanham a tabela.

## Security
_Cybersec_ — n/a

## Rollback
Sem migração destrutiva. Reverter o código depois de merges feitos deixa eventos `PR_MERGED` no banco que o daemon antigo não reconhece (`UnknownEventTypeException` ao ler o card): antes de reverter, desligar o modo autônomo do projeto ou remover esses eventos.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso heavy: duas camadas (`core` e `daemon`) e 7 ACs; sem seção de segurança, mas o merge é a ação mais irreversível da fábrica, por isso `expectedHead` obrigatório.
- "Volta ao dono" é Staged: é a coluna de decisão do dono e já tem rota própria para uma nova aprovação; exigiu a linha nova `READY_FOR_PROD→STAGED` pela fábrica.
- Entrega determinística no daemon (sem agente nem skill): é só chamada de Forge; roda por gancho do `Dispatcher` (respeita PAUSE, horário e limite diário) em thread própria, não como processo.
- `PR_MERGED` é evento próprio: sem ele o reinício depois do merge e antes do Done repetiria o merge ou perderia o commit a acompanhar (AC-7).
- Prazo do CI da main reaproveita `maxPolls` e `CiPause` do #45 (injetados, testes sem relógio real); estouro devolve o card ao dono em vez de Done.
- Novo arquivo de teste `AutonomousDeliveryTest.kt`: nenhuma suíte existente cobre a entrega após a aprovação do dono.
- CI vermelho na main sem revert e conflito sem resolução ficam para #49 e #50, conforme o roadmap.

## Open questions
- none

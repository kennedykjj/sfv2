# PR mergeado fora da fábrica — spec

**Issue:** #48 · **Branch:** `feat/48-pr-mergeado-fora-da-fabrica` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Na entrega autônoma, se o PR de um card em Ready for Prod já foi mergeado fora da fábrica (pelo dono, na web do GitHub), o card volta a Staged com `pr_not_open` e fica preso, embora o trabalho esteja entregue. Lição da issue #282 da V0: card não pode ficar preso.
**Audience:** O desenvolvedor sozinho com muitos projetos, que às vezes mergeia na mão; e o desenvolvedor dentro de uma empresa, onde o merge costuma ser feito por outra pessoa ou regra do repositório e a trilha de auditoria precisa dizer quem mergeou.
**Acceptance criteria**
- **AC-1** — Dado um card em Ready for Prod cujo PR já está mergeado quando a entrega o encontra, quando a entrega roda, então o card vai a Done sem novo merge e sem esperar o CI da main.
- **AC-2** — Dado o caso do AC-1, quando o card chega a Done, então o histórico tem um evento de PR mergeado marcado como externo (feito fora da fábrica) e o evento de movimento a Done, e a mensagem diz que o merge foi feito fora da fábrica.
- **AC-3** — Dado um PR achado aberto que é mergeado fora entre a consulta e o merge da fábrica (resposta NotOpen ou Merged), quando a entrega trata essa resposta, então o resultado é o mesmo do AC-1 e AC-2.
- **AC-4** — Dado um PR mergeado pela própria fábrica, quando a entrega termina, então o evento de merge não é marcado como externo (comportamento atual preservado).
- **AC-5** — Dado um PR fechado sem merge, quando a entrega o encontra, então o card volta a Staged com motivo `pr_not_open`.
- **AC-6** — Dado que o forge falha ao consultar o PR, quando a entrega roda, então o card volta a Staged com motivo `forge_failed` e não fica travado em Ready for Prod.
**Out of scope:** Detectar merge externo fora do momento da entrega (sem polling nem sincronização contínua); entrega no PR (modo sem merge da fábrica); fechar issue ou ticket externo; mudanças de UI; reabrir ou reverter PR.
**Product fit:** Serve "A fábrica é dona do próprio estado" e "Toda ação é um evento com ator": o merge externo fica registrado como tal, sem atribuí-lo à fábrica. Não contradiz o posicionamento nem o que está fora de escopo; a consulta ao forge ocorre só no momento da entrega, sem sincronização contínua.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contrato (porta `Forge`):** novo `fun findMergedPullRequest(branch: String): MergedPullRequest?` e `data class MergedPullRequest(val number: Int, val mergeCommit: String)`. Devolve o PR mais recente da branch já mergeado, ou `null`. Falha do forge lança `ForgeException`.

**Camadas e arquivos:**
- `ports`: `ports/src/main/kotlin/sfv2/ports/Forge.kt` (método e data class); `ports/src/main/kotlin/sfv2/ports/FakeForge.kt` (acha entrada `MERGED` pela branch, devolve `merge-<n>`, respeita `failNext("findMergedPullRequest")`).
- `adapters`: `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt` — `gh pr list --head <branch> --state merged --limit 1 --json number,mergeCommit`, mesma validação de repo/branch do `findOpenPullRequest`; `mergeCommit.oid` ausente → `ForgeException`.
- `daemon`: `daemon/src/main/kotlin/sfv2/daemon/AutonomousDelivery.kt` — nos dois pontos que hoje devolvem `pr_not_open` (nenhum PR aberto; `MergeResult.NotOpen`), consulta `findMergedPullRequest(branch)`. Se achar: grava `PR_MERGED` com `pr`, `merge_commit` e `origin=external`, move o card a `DONE` com `merge_commit`, `origin=external` e `reason=merged_outside_factory`, loga `delivery_done` com `origin=external` e devolve `DeliveryOutcome.Done`. Não segue o CI da main. Se não achar: `pr_not_open`, como hoje. `ForgeException` na consulta: `forge_failed`. `daemon/src/main/kotlin/sfv2/daemon/ProjectDelivery.kt` — `UnavailableForge` implementa o método lançando a mesma exceção dos demais.
- O merge feito pela fábrica segue gravando `PR_MERGED` sem `origin` (AC-4); o `PR_MERGED` externo não é lido como merge da fábrica no replay, pois o card sai de Ready for Prod no mesmo passo.

**Migração:** nenhuma (sem esquema novo; payload de evento é mapa livre).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- A porta `Forge` ganha `findMergedPullRequest` porque o PR mergeado fora não é achável por `findOpenPullRequest` e o número do PR não é persistido antes do merge; sem isso o daemon não distingue "mergeado fora" de "fechado sem merge".
- Merge externo vai a Done sem seguir o CI da main: a issue pede "sem bloquear", e a fábrica não mergeou, então não responde por aquela main.
- Os testes da porta e do adaptador (`ForgeTest`, `GhForgeTest`) cobrem o contrato novo dentro do trabalho das camadas `ports` e `adapters`; o plano mapeia os ACs de produto ao daemon, onde o comportamento é observável.
- Peso `heavy`: toca 3 camadas (ports, adapters, daemon).

## Open questions
- none

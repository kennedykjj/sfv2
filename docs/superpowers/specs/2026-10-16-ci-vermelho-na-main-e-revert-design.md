# CI vermelho na main e revert — spec

**Issue:** #50 · **Branch:** `feat/50-ci-vermelho-na-main-e-revert` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Hoje, quando o CI da main fica vermelho após o merge de um card, a fábrica devolve o card ao dono (Staged, motivo `main_ci_red`) na primeira falha. Falha intermitente (flaky) vira trabalho manual e, quando a quebra é real, o dono não recebe uma saída pronta. O merge que quebra a main precisa de uma saída automática e visível.
**Audience:** O desenvolvedor sozinho com muitos projetos, que não acompanha a main enquanto a fábrica trabalha (inclusive com o app fechado); vale também para o desenvolvedor em empresa, que precisa ver a quebra e a correção proposta.
**Acceptance criteria**
- **AC-1** — Dado um card em entrega autônoma cujo merge deixou o CI da main vermelho, quando a fábrica detecta o vermelho pela primeira vez, então ela reroda o CI uma única vez e acompanha o novo resultado; se ficar verde, o card fecha como Done e nenhum PR de revert é aberto.
- **AC-2** — Dado que o CI rerodado na main também ficou vermelho, quando a fábrica detecta o segundo vermelho, então ela abre um PR de revert do merge, o card volta ao dono em Staged e o número do PR de revert fica registrado e visível ao dono no card.
- **AC-3** — Dado um CI vermelho na main, quando o rerun ainda não terminou, então o card não é fechado nem devolvido ao dono e o rerun não é disparado uma segunda vez (inclusive após reinício do daemon).
- **AC-5** — Dado que o rerun acabou de ser pedido e o forge ainda devolve o vermelho antigo do mesmo commit, quando a fábrica volta a consultar, então esse vermelho não conta como segundo vermelho: só vale um `Failed` visto depois de ter aparecido um `Pending` desde o rerun; sem nenhum `Pending` até o esgotamento das consultas, o card volta a Staged com `rerun_not_started` e nenhum revert é aberto.
- **AC-6** — Dado que a chamada de rerun falhou (`ForgeException`), quando o dono aprova de novo, então a fábrica ainda tem o rerun a fazer (a tentativa que falhou não conta) e não abre revert.
- **AC-4** — Dado que a fábrica abriu o PR de revert, quando o dono olha o card ou o histórico, então ele vê um evento do rerun e o evento de volta ao dono com o motivo (checks vermelhos) e o número do PR de revert, ambos com ator, sem precisar abrir o GitHub para descobrir o que houve.
**Out of scope:** Mergear o revert automaticamente (o dono decide); conflito com a main (#49); PR mergeado fora da fábrica (#48); modo de entrega no PR; mais de um rerun.
**Product fit:** Serve "gates são scripts, não a palavra do agente" (o verde do CI decide o fechamento) e "toda ação é um evento com ator". Não contradiz o posicionamento: o revert só é aberto, nunca mergeado, então a fábrica não decide sozinha o que vai para produção.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core` `Event.kt`: novo `EventType.MAIN_CI_RERUN` (payload `merge_commit`, `pr`), gravado antes de chamar o rerun.
- `ports` `Forge.kt`: `rerunChecks(head: String)` (reroda só os jobs que falharam do merge commit) e `revert(pr: PullRequestRef): PullRequestRef` (abre o PR de revert do PR mergeado e devolve o PR aberto). `FakeForge` ganha as duas operações, `failNext` cobre ambas e o fake expõe `reruns` e `reverts`.
- `adapters` `GhForge.kt`: `rerunChecks` = `gh api repos/$repo/actions/runs?head_sha=<sha>` e `POST actions/runs/{id}/rerun-failed-jobs` para cada run concluído com falha; `revert` = `gh pr view --json id` e mutation GraphQL `revertPullRequest` via `gh api graphql`. `head` e `merge commit` seguem a validação `^[0-9a-f]{40}$` do #44 e os ids de run vêm do JSON como inteiros, nunca de texto livre. Mesma regra de erro: `ForgeException` com operação e número do PR, nunca saída do `gh`.
- `daemon` `AutonomousDelivery.followMain`: no primeiro `ChecksState.Failed`, sem evento `MAIN_CI_RERUN` para o merge commit, chama `forge.rerunChecks` e, só depois de a chamada dar certo, grava o evento (gravá-lo antes gastaria o único rerun numa chamada que falhou e levaria direto ao revert; uma queda entre a chamada e o evento pode no máximo repetir o rerun, o que é inofensivo), e volta a acompanhar (mesmo `maxPolls`, mesma pausa), ignorando o `Failed` até ter visto um `Pending` depois do rerun (o vermelho antigo ainda aparece por instantes e viraria um falso segundo vermelho e um revert desnecessário). Com `MAIN_CI_RERUN` já gravado (reinício incluído) e `Failed`, não reroda: chama `forge.revert` com o PR do `PR_MERGED` e devolve o card a Staged com `reason` `main_ci_red ...` e `revert_pr=<n>` no payload. `ForgeException` no rerun ou no revert devolve o card a Staged com `forge_failed` (e `revert_failed` quando só o revert falha, mantendo o vermelho no motivo). Timeout (`Pending`) continua `main_ci_timeout`.

**Camadas e módulos:** `core`, `ports`, `adapters`, `daemon`.

**Migração:** nenhuma. O tipo de evento novo é aditivo; o `events.type` é texto e eventos antigos continuam legíveis.

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
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |

O teste existente "AC-5 a red main goes back to the owner..." muda: vermelho agora passa pelo rerun. `ForgeTest.kt` (ports) e `GhForgeTest.kt` (adapters) cobrem as duas operações novas do fake e do adapter como parte da implementação.

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy` (a issue previa standard): a entrega toca 4 camadas (core, ports, adapters, daemon) porque o Forge ainda não reroda CI nem abre revert.
- Evento novo `MAIN_CI_RERUN` em vez de contador em memória: é o que garante um único rerun após reinício do daemon (AC-3) e dá o evento visível do AC-4.
- Revert via mutation GraphQL `revertPullRequest`: abre o PR sem checkout nem push locais, então o adapter não precisa de git.
- O revert nunca é mergeado pela fábrica; o card volta a Staged com `revert_pr` no payload e o dono decide.
- Se o rerun ainda mostrar o resultado antigo logo após disparar, a espera (`pause`) antes do primeiro poll pós-rerun evita ler o vermelho velho.
- Testes novos entram nos arquivos existentes (`AutonomousDeliveryTest`, `ForgeTest`, `GhForgeTest`); nenhum arquivo de teste novo.

## Open questions
- none

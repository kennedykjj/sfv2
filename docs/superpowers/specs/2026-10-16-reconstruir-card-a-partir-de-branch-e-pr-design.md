# Reconstruir card a partir de branch e PR — spec

**Issue:** #51 · **Branch:** `feat/51-reconstruir-card-a-partir-de-branch-e-pr` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Máquina trocada ou banco apagado hoje perde o estado dos cards, embora a branch e o PR de cada card continuem no GitHub. O trabalho não pode se perder junto com o banco.
**Audience:** O desenvolvedor sozinho que troca de máquina ou perde o banco local, e o dentro de empresa que precisa recuperar o estado sem refazer a entrega.
**Acceptance criteria**
- **AC-1** — Dado um banco apagado e um PR aberto da fábrica, quando o dono roda `sfv2 card rebuild`, então o card é recriado com o mesmo id e título do PR na coluna Staged.
- **AC-2** — Dado um PR da fábrica já mergeado, quando roda `sfv2 card rebuild`, então o card é recriado na coluna Done.
- **AC-3** — Dado um PR da fábrica fechado sem merge, quando roda `sfv2 card rebuild`, então nenhum card é criado para ele.
- **AC-4** — Dado um PR cuja branch não segue o formato de branch do projeto para o id do card, ou cujo id não é válido, quando roda `sfv2 card rebuild`, então ele é ignorado e nada é gravado.
- **AC-5** — Dado um card que já existe no banco, quando roda `sfv2 card rebuild` (uma ou várias vezes), então esse card não é alterado nem duplicado.
- **AC-6** — Dado um card recriado, quando se lê o histórico, então há um evento de criação do dono marcado como reconstruído, com o número do PR, e o card pertence ao projeto.
- **AC-7** — Dado que o forge falha ao listar os PRs, quando roda `sfv2 card rebuild`, então o comando termina com erro que nomeia a operação e nada é gravado.
- **AC-8** — Dado o forge falso e a camada e2e, quando o mesmo card percorre a entrega autônoma e a entrega no PR, então chega a Done nos dois modos; e apagar o banco e reconstruir recupera o estágio (Staged com PR aberto, Done com PR mergeado).
**Out of scope:** Recuperar cards sem PR (Inbox até Validation), histórico anterior, custos e eventos de gate; reconstruir automaticamente na subida do daemon; PR fechado sem merge; cards importados e suas referências externas (fase 4).
**Product fit:** Serve "A fábrica é dona do próprio estado": o estado mínimo volta do forge, sem depender do banco. Não contradiz o posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:**
- `Forge` (porta): `fun listPullRequests(): List<PullRequestSummary>` com `data class PullRequestSummary(val number: Int, val branch: String, val title: String, val state: PullRequestState)`; `PullRequestState` é o existente (`Open`, `Merged`, `ClosedUnmerged`). Falha do forge lança `ForgeException`.
- Protocolo do daemon: `Request.CardRebuild(actor, project: String?, proof)` (`card.rebuild`, só dono, com prova) e resposta `Response.CardsResult` com os cards recriados.
- CLI: `sfv2 card rebuild [--project <nome>]`; imprime uma linha por card recriado (mesmo formato de `card list`).

**Camadas e arquivos:**
- `ports`: `ports/src/main/kotlin/sfv2/ports/Forge.kt`, `ports/src/main/kotlin/sfv2/ports/FakeForge.kt` (lista as entradas com estado; respeita `failNext("listPullRequests")`).
- `adapters`: `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt` — `gh pr list --repo <repo> --state all --json number,headRefName,title,state,mergedAt`, mesma validação de repo do restante; `MERGED` → `Merged`, `CLOSED` → `ClosedUnmerged`, `OPEN` → `Open(head)`.
- `daemon`: `daemon/src/main/kotlin/sfv2/daemon/CardRebuild.kt` (novo; lógica), `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt` (despacho), `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt` (parse de `card.rebuild`).
- `cli`: `cli/src/main/kotlin/sfv2/cli/CardCommands.kt` (`CardRebuild`, registrado em `withCardCommands`).
- `e2e`: teste de saída da fase 3.

**Regra de reconstrução:** para cada PR listado, o id do card sai da branch casando o `branchFormat` do projeto com `{id}` = UUID válido; só vale se `branchName(branchFormat, id, title)` reconstruir exatamente a branch do PR (o título do PR é o do card). Corpo e título do PR são dado não confiável e nunca viram instrução. `Open` → `STAGED`; `Merged` → `DONE`; `ClosedUnmerged` ou branch que não casa → ignorado. Card com o mesmo id já no banco → ignorado. Cada card novo é gravado por `recorder.record` com um evento `CARD_CREATED` do ator dono, payload `title`, `origin=rebuilt`, `pr=<número>`, e `store.assignProject`. Projeto: `--project` ou, sem a opção, o único projeto registrado; zero ou vários projetos sem a opção → erro. Log `key=value` só com ids, sem título.

**Migração:** nenhuma (payload de evento é mapa livre; sem esquema novo).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardRebuildTest.kt` |
| AC-8 | e2e | `e2e/src/test/kotlin/sfv2/e2e/PhaseThreeExitTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Um arquivo novo `CardRebuildTest.kt` no daemon: nenhuma suíte cobre o comando novo (`CardCommands` não tem arquivo de teste próprio) e `SocketServerTest` é de transporte.
- Um arquivo novo `PhaseThreeExitTest.kt` no e2e: `PhaseExitTest` é a saída da fase 2 e o AC pede a saída da fase 3.
- Os contratos de `Forge`/`GhForge` e a ligação da CLI (`MainTest`) são testados no trabalho de cada camada; o plano mapeia os ACs de produto ao daemon, onde o comportamento é observável.
- A porta ganha `listPullRequests` porque, sem banco, o daemon não conhece nenhuma branch; `findOpenPullRequest`/`findMergedPullRequest` exigem a branch.
- Id do card vem da branch validada contra o `branchFormat`, não do corpo do PR: o corpo é texto externo e a branch é verificável por reconstrução exata.
- PR fechado sem merge não é recriado: o trabalho foi descartado e não há coluna compatível.
- Recriação é um evento `CARD_CREATED` do dono marcado `origin=rebuilt`, sem simular os movimentos intermediários: o histórico perdido não é inventado.
- Só o dono reconstrói (prova do dono), como as demais mutações pela CLI.
- Peso `heavy`: toca 5 camadas (ports, adapters, daemon, cli, e2e).

## Open questions
- **Escopo:** A (recomendado) separar em um card de daemon (porta, adaptador e lógica) antes deste, que ficaria só com CLI e e2e — a nota da issue pede isso quando fica grande, e aqui são 5 camadas; ou B manter tudo neste card — um card grande, mas sem novo bloqueio.

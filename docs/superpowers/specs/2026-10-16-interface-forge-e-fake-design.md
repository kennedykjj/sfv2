# Interface Forge e fake — spec

**Issue:** #43 · **Branch:** `feat/43-interface-forge-e-fake` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** a entrega (abrir PR, ler CI, mergear) precisa ficar atrás de uma interface Forge, com um fake, para que o fluxo de entrega seja testável sem rede nem GitHub real.
**Audience:** o desenvolvedor sozinho com muitos projetos (e o dentro de empresa), que depende de uma entrega confiável em modo autônomo ou entrega no PR.
**Acceptance criteria**
- **AC-1** — Dado a interface Forge, quando um chamador abre um PR, então ela devolve a referência do PR criado (branch de origem e head).
- **AC-2** — Dado um PR com checks, quando se lê os checks de um head, então a interface devolve o estado dos checks daquele head e, para cada check que falhou, o nome e um resumo da falha (texto de CI é dado não confiável: até 2048 bytes por check, nunca vira instrução para agente).
- **AC-3** — Dado um PR aberto, quando se pede o merge informando o head esperado e o head atual é o mesmo, então o PR é mergeado e o resultado traz o SHA do commit de merge na base (o acompanhamento do CI na main e o revert dependem dele).
- **AC-4** — Dado um PR cujo head atual difere do head informado, quando se pede o merge, então o merge é recusado e o PR continua aberto.
- **AC-5** — Dado um PR, quando se lê seu estado, então a interface distingue aberto, mergeado e fechado sem merge.
- **AC-7** — Dado um PR aberto cuja base andou e que não mergeia limpo, quando se pede o merge com o head certo, então o resultado é `Conflict` e o PR continua aberto (conflito não é falha de rede nem divergência de head).
- **AC-8** — Dado uma branch que já tem PR aberto, quando um chamador abre PR para ela, então a interface devolve o PR existente, atualizando título e corpo, e não cria um segundo; e `findOpenPullRequest(branch)` devolve o PR aberto da branch ou nulo (validate abre ou atualiza; `card rebuild` reconstrói a partir da branch).
- **AC-9** — Dado o forge sem rede ou com erro, quando qualquer operação falha, então ela lança `ForgeException` com mensagem que diz a operação e o número do PR (nunca o corpo do PR nem log de CI), e o fake pode roteirizar essa falha sem gravar nada pela metade.
- **AC-6** — Dado o forge falso, quando um teste roteiriza CI verde, CI vermelho, PR mergeado fora da fábrica e PR fechado, então cada leitura devolve o cenário roteirizado, sem rede.
**Out of scope:** implementação real contra o GitHub; escolha do modo de entrega; política de quando mergear; comentários, reviews e demais operações de forge.
**Product fit:** serve ao princípio "gates são scripts, não a palavra do agente" (CI lido do forge decide) e a "nada sai da máquina sem evento assinado" (merge exige head exato). Não contradiz posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `ports` (sem dependência nova; segue o padrão de `Runtime`/`FakeRuntime`).

**Contratos** (`ports/src/main/kotlin/sfv2/ports/Forge.kt`):
- `interface Forge`:
  - `openPullRequest(request: PullRequestRequest): PullRequestRef`
  - `checks(head: String): ChecksState`
  - `merge(pr: PullRequestRef, expectedHead: String): MergeResult`
  - `findOpenPullRequest(branch: String): PullRequestRef?`
  - `state(pr: PullRequestRef): PullRequestState`
- `data class PullRequestRequest(branch: String, base: String, title: String, body: String)`
- `data class PullRequestRef(number: Int, branch: String, head: String)`
- `sealed interface ChecksState`: `Pending`, `Passed`, `Failed(failing: List<FailedCheck>)`, com `data class FailedCheck(name: String, summary: String)` (`summary` truncado em 2048 bytes UTF-8)
- `sealed interface MergeResult`: `Merged(mergeCommit: String)`, `HeadMismatch(actualHead: String)`, `Conflict`, `NotOpen(state: PullRequestState)`
- `sealed interface PullRequestState`: `Open(head: String)`, `Merged`, `ClosedUnmerged`

**Fake** (`ports/src/main/kotlin/sfv2/ports/FakeForge.kt`): `class FakeForge` mantém PRs em memória, sem IO, relógio ou rede.
- Roteiro: `scriptChecks(head, ChecksState)`; `mergedOutside(pr)` e `closeOutside(pr)` simulam PR mergeado/fechado fora da fábrica; `pushHead(pr, head)` simula novo commit.
- `openPullRequest` numera os PRs em sequência e registra em `opened`; `checks` de head sem roteiro devolve `Pending`; `merge` só vira `Merged` quando o PR está aberto e `expectedHead` é o head atual (registra em `merges`); `state` devolve o estado atual.
- `openPullRequest` de branch que já tem PR aberto atualiza título e corpo e devolve o mesmo `PullRequestRef`; `findOpenPullRequest` devolve o PR aberto da branch ou nulo; `scriptConflict(pr)` faz o próximo `merge` devolver `Conflict`; `failNext(operation)` faz a próxima chamada lançar `ForgeException` sem alterar o estado. `class ForgeException(message: String) : RuntimeException(message)`.
- PR desconhecido lança `UnknownPullRequestException` (`RuntimeException`, mensagem com o número).

Sem arquivos fora de `ports/` (dono: `ports`). Sem migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-2 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-3 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-4 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-5 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-6 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-7 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-8 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |
| AC-9 | ports | `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Peso `standard`: uma camada, 9 ACs, 3 arquivos (Forge.kt, FakeForge.kt, ForgeTest.kt), sem UI nem segurança.
- AC-7 a AC-9 e o SHA de merge vieram da auditoria dos consumidores: #45 (abre ou atualiza o PR, resume a falha do CI), #46 e #50 (acompanham o CI na main, precisam do commit de merge), #49 (conflito), #44 (erro de rede) e #51 (reconstrói a partir da branch).
- `ForgeTest.kt` é arquivo novo: o único suite existente em `ports` cobre `Runtime`, outra port.
- Merge devolve `MergeResult` (sealed) em vez de lançar: recusa por head divergente é resultado esperado, não falha.
- Estado dos checks é por head (não por PR), como o card pede ("lê checks de um head").
- Bloqueio #42 já está fechado; segue sem pendência.

## Open questions
- none

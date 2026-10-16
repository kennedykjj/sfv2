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
- **AC-2** — Dado um PR com checks, quando se lê os checks de um head, então a interface devolve o estado dos checks daquele head.
- **AC-3** — Dado um PR aberto, quando se pede o merge informando o head esperado e o head atual é o mesmo, então o PR é mergeado.
- **AC-4** — Dado um PR cujo head atual difere do head informado, quando se pede o merge, então o merge é recusado e o PR continua aberto.
- **AC-5** — Dado um PR, quando se lê seu estado, então a interface distingue aberto, mergeado e fechado sem merge.
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
  - `state(pr: PullRequestRef): PullRequestState`
- `data class PullRequestRequest(branch: String, base: String, title: String, body: String)`
- `data class PullRequestRef(number: Int, branch: String, head: String)`
- `sealed interface ChecksState`: `Pending`, `Passed`, `Failed(failing: List<String>)`
- `sealed interface MergeResult`: `Merged`, `HeadMismatch(actualHead: String)`, `NotOpen(state: PullRequestState)`
- `sealed interface PullRequestState`: `Open(head: String)`, `Merged`, `ClosedUnmerged`

**Fake** (`ports/src/main/kotlin/sfv2/ports/FakeForge.kt`): `class FakeForge` mantém PRs em memória, sem IO, relógio ou rede.
- Roteiro: `scriptChecks(head, ChecksState)`; `mergedOutside(pr)` e `closeOutside(pr)` simulam PR mergeado/fechado fora da fábrica; `pushHead(pr, head)` simula novo commit.
- `openPullRequest` numera os PRs em sequência e registra em `opened`; `checks` de head sem roteiro devolve `Pending`; `merge` só vira `Merged` quando o PR está aberto e `expectedHead` é o head atual (registra em `merges`); `state` devolve o estado atual.
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

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Peso `light`: uma camada, 6 ACs mas só 3 arquivos (Forge.kt, FakeForge.kt, ForgeTest.kt), sem UI nem segurança.
- `ForgeTest.kt` é arquivo novo: o único suite existente em `ports` cobre `Runtime`, outra port.
- Merge devolve `MergeResult` (sealed) em vez de lançar: recusa por head divergente é resultado esperado, não falha.
- Estado dos checks é por head (não por PR), como o card pede ("lê checks de um head").
- Bloqueio #42 já está fechado; segue sem pendência.

## Open questions
- none

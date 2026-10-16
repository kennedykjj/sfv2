# Validate abre o PR e espera o CI — spec

**Issue:** #45 · **Branch:** `feat/45-validate-abre-o-pr-e-espera-o-ci` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** Hoje um card pode chegar a Staged só com o validate local verde; Staged não garante que o PR passou no CI. O dono precisa confiar que Staged significa PR verde.
**Audience:** O desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de uma empresa, que revisa PRs já verdes).
**Acceptance criteria**
- **AC-1** — Dado um card em Validation com o validate verde, quando o daemon prossegue, então o PR do card é aberto pelo Forge; se já existir PR para a branch, ele é atualizado e não duplicado.
- **AC-2** — Dado o PR aberto ou atualizado com o CI ainda pendente, quando o daemon consulta o Forge, então o card permanece em Validation e não vai a Staged.
- **AC-3** — Dado o CI do PR verde, quando o daemon o observa, então o card vai a Staged e o evento registra a decisão com o resultado do CI.
- **AC-4** — Dado o CI do PR vermelho, quando o daemon o observa, então o card volta a Coding com um resumo do log da falha (só o trecho relevante, texto tratado como dado não confiável) e o card nunca passa por Staged.
**Out of scope:** Merge do PR (Ready for Prod e modo de entrega); política de retentativas e limite de rodadas além do já existente; reexecutar CI manualmente; UI para exibir o resumo; novos adapters de Forge além de GhForge.
**Product fit:** Serve ao princípio "gates são scripts, não a palavra do agente": o que move o card é o CI verde, não o relato do agente. Não contradiz posicionamento nem o escopo excluído; o toque no GitHub é o PR do próprio card, marco da entrega.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon`. Contratos de `ports` (`Forge`, `ChecksState`, `FakeForge`) já existem e não mudam.

- `daemon/src/main/kotlin/sfv2/daemon/PullRequestDelivery.kt` (novo): `class PullRequestDelivery(forge: Forge, publisher: BranchPublisher, pause: CiPause, maxPolls: Int, ...)` com `fun deliver(cardId, branch, title, body, worktree): CiResult`. Empurra a branch (`BranchPublisher.push(worktree)`), chama `forge.openPullRequest` (idempotente por branch: reabre/atualiza), consulta `forge.checks(head)` até sair de `Pending` ou estourar `maxPolls`.
- `CiResult` (sealed): `Green`, `Red(summary)`, `TimedOut`, `ForgeFailed`. `Red.summary` = nomes dos checks que falharam + `summary` de cada um (já truncado a 2048 bytes pelo adapter), nunca logado inteiro.
- `fun interface BranchPublisher { fun push(worktree: Path): PushResult }` e `fun interface CiPause { fun await() }` (a espera entra por parâmetro; o teste usa fake sem relógio real). Implementação de produção do push em `WorktreeGit.kt` (git sem shell, hooks desligados, como `SystemBranchSync`).
- `ValidationOutcome.apply(cardId, report, gates, ci: () -> CiResult)`: `ci` só é chamado quando o veredito é APPROVED e os gates estão verdes. `Green` → Staged (payload `ci=green`); `Red` → CODING com `layer` e `reason` = resumo; `TimedOut`/`ForgeFailed` → nova `ValidationDecision.CiUnresolved` (card fica em Validation).
- `ValidatePipeline.run`: monta `ci` com `PullRequestDelivery`; `CiUnresolved` → `block(cardId, "ci_timeout"|"forge_failed")`. `Returned` por CI volta a Coding sem consumir a rodada de review seguinte.
- Camada devolvida no CI vermelho: a que contém mais arquivos alterados contra a main (reuso de `GitChangedPaths`); sem alteração, a primeira camada do projeto.
- Sem migração, sem mudança de contrato externo. Arquivos: `PullRequestDelivery.kt`, `ValidationOutcome.kt`, `ValidatePipeline.kt`, `WorktreeGit.kt`, `ValidatePipelineTest.kt`, `ValidationOutcomeTest.kt`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso standard: uma camada, 4 ACs, 6 arquivos, sem UI nem segurança.
- O push da branch é responsabilidade do daemon (`BranchPublisher`): o `Forge` só abre o PR e o head vem do push, então sem push o CI nunca roda.
- Espera do CI por polling com `maxPolls` e `CiPause` injetados: sem `Thread.sleep` nos testes; estouro bloqueia o card com `ci_timeout` em vez de aprovar.
- CI vermelho não gasta rodada de review: o card sai de Validation e a próxima passagem é um novo validate após a correção.
- Falha do Forge (rede/API) bloqueia o card com `forge_failed`; nunca vira Staged.
- Arquivos de teste existentes reaproveitados (`ValidatePipelineTest`, `ValidationOutcomeTest`); nenhum arquivo de teste novo.

## Open questions
- none

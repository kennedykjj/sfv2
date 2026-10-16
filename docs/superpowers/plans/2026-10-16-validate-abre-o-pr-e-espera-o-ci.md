# Validate abre o PR e espera o CI — plano (#45)

Camada única: `daemon` (agente `engineer`, execução solo do Tech Lead: peso standard, uma camada).
Spec: `docs/superpowers/specs/2026-10-16-validate-abre-o-pr-e-espera-o-ci-design.md`.

## Contratos (para os testes de aceitação)

Pacote `sfv2.daemon`, dependências em `sfv2.ports` (`Forge`, `FakeForge`, `ChecksState`, `PullRequestRequest`):

- `sealed interface CiResult { data object Green; data class Red(val summary: String); data object TimedOut; data object ForgeFailed }`
- `fun interface BranchPublisher { fun push(worktree: Path, branch: String): PushResult }`, `sealed interface PushResult { Pushed; Refused; Failed }`
- `fun interface WorktreeHead { fun of(worktree: Path): String }`
- `fun interface CiPause { fun await() }`
- `class PullRequestDelivery(forge: Forge, publisher: BranchPublisher, head: WorktreeHead, pause: CiPause, maxPolls: Int = 40, base: String = "main")` com `fun deliver(cardId, branch, title, body, worktree): CiResult`; `Refused` de push → exceção `PushRefusedException` (a pipeline bloqueia com `push_refused`).
- `class SystemBranchPublisher : BranchPublisher` em `WorktreeGit.kt` (refspec explícito, sem `--force`/`--tags`, recusa branch inválida ou igual à base).
- `ValidationOutcome.apply(cardId, report, gates, ci: () -> CiResult)`; `Green` → Staged com payload `ci=green`, `head`; `Red` → CODING com `layer` + `reason`; `TimedOut`/`ForgeFailed` → `ValidationDecision.CiUnresolved(reason)`.
- `ValidatePipeline(..., delivery: PullRequestDelivery? = null)` (nulo = sem etapa de CI, usado por testes/e2e antigos); `CiUnresolved` → `block("ci_timeout"|"forge_failed")`; push recusado → `block("push_refused")`.

## Tarefas

| # | Camada | Dono | Arquivos |
|---|---|---|---|
| 1 | daemon | engineer (solo) | `PullRequestDelivery.kt`, `ValidationOutcome.kt`, `ValidatePipeline.kt`, `WorktreeGit.kt`, `RefineRuntime.kt` (fiação) |

## Testes de aceitação (QA)

`ValidatePipelineTest.kt` (AC-1, 2, 5, 6) e `ValidationOutcomeTest.kt` (AC-3, 4, 7), com `FakeForge` e fakes de `BranchPublisher`, `WorktreeHead` e `CiPause`.

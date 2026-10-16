# Plano — gates com ambiente mínimo e montagem única dos estágios (#163)

Spec: `docs/superpowers/specs/2026-10-16-gates-com-ambiente-minimo-e-montagem-unica-dos-design.md`

Camada única: `daemon`. Agente: `engineer`. Tarefas em sequência (compartilham o worktree).

## Tarefa 1 — daemon: ambiente mínimo no ProcessGateRunner (AC-1..3)
- Arquivo: `daemon/src/main/kotlin/sfv2/daemon/ProcessGateRunner.kt`.
- `minimalEnvironment(source: Map<String, String>): Map<String, String>` pura (PATH, HOME, JAVA_HOME, LANG, `LC_*`, `GRADLE_*`).
- `ProcessBuilder.environment()` limpo e repovoado a partir de `System.getenv()` antes do `start()`.

## Tarefa 2 — daemon: fonte única de maxRounds (AC-4, AC-5)
- `RefinePipeline.kt`: `const val DEFAULT_MAX_ROUNDS = 3` no nível do arquivo; default de `maxRounds` usa-a.
- `DevelopPipeline.kt` e `ValidatePipeline.kt`: default passa a `DEFAULT_MAX_ROUNDS`. Se existir `DEFAULT_VALIDATE_MAX_ROUNDS` (do #162), apagá-la e usar só `DEFAULT_MAX_ROUNDS`.

## Tarefa 3 — daemon: ValidateStageLauncher delega a PipelineStageLauncher (AC-6, AC-7)
- `ValidatePipeline.kt`: `ValidateStageLauncher` delega a `PipelineStageLauncher("validate", ...)`, como `RefineStageLauncher`; remover thread, `execute` e `awaitLock` próprios.
- `RefineRuntime.kt`: um só `StageRouter`; sem mudança de montagem esperada.

## Tarefa 4 — docs: spec do develop (AC-8)
- `docs/superpowers/specs/2026-10-16-pipeline-de-develop-design.md`: texto de `maxRounds` alinhado à fonte única `DEFAULT_MAX_ROUNDS`. (Dono: Tech Lead, arquivo sem camada.)

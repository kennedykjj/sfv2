# Pipeline de validate — spec

**Issue:** #41 · **Branch:** `feat/41-pipeline-de-validate` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** Gates, revisão (skill de validate) e dispatcher existem separados; nada os liga. Um card em Validation não chega sozinho a uma branch validada e pronta para o dono.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, igualmente, o que trabalha numa empresa): quer que a fábrica valide em segundo plano e só o chamar onde a decisão é dele.
**Acceptance criteria**
- **AC-1** — Dado um card em Validation e uma vaga de execução, quando o dispatcher roda, então a branch é atualizada com a main, os gates (build/teste por camada, escopo, spec) rodam de novo sobre ela e a revisão (skill de validate) acontece, com os eventos registrados com ator.
- **AC-2** — Dado gates todos verdes e revisão com VERDICT APPROVED, quando o resultado é avaliado, então o card move para Staged.
- **AC-3** — Dado revisão APPROVED mas algum gate vermelho, quando o resultado é avaliado, então o card não vai para Staged e fica bloqueado com um motivo legível que lista os gates que falharam.
- **AC-4** — Dado revisão com achado bloqueante, quando o resultado é avaliado, então o card volta a Coding com a camada e o motivo do achado registrados.
- **AC-5** — Dado conflito ao atualizar com a main, ou relato de revisão malformado em todas as rodadas até o máximo, quando o pipeline termina, então o card fica bloqueado com motivo legível para o dono e nunca vai a Staged (no conflito, sem rodar a revisão).
- **AC-6** — Dado um run de validate, quando a sessão MCP é aberta, então o arquivo de configuração (com o token) é escrito FORA do worktree, com permissão 0600, e ao fim do run (sucesso, falha, exceção ou timeout) o arquivo é apagado, a sessão é revogada e o lock do card no `RunPool` é solto; nenhum `mcp-config` aparece no diff do card.
**Out of scope:** Abrir PR e esperar CI (fase 3); aprovação do dono e estágios depois de Staged; conteúdo e qualidade da skill de validate e dos gates em si; UI/app; definição do máximo de rodadas (vem da configuração existente); desbloqueio do card pelo dono.
**Product fit:** Serve "gates são scripts, não a palavra do agente" (revisão APPROVED não vence gate vermelho) e "o dono aparece só onde a decisão é dele" (bloqueios sempre com motivo). Não contradiz posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Só o módulo `daemon`; nenhum contrato externo (socket, MCP, eventos) muda. Reusa `ValidationOutcome` (#38), `GateExecutor`, `SpecGates`, `ScopeGate` e o molde de `RefinePipeline`.

- `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt` (novo): `ValidatePipeline.run(cardId)` executa o estágio com o card em `VALIDATION`. Fluxo por passagem: `WorktreeManager.ensure` → `BranchSync.mergeMain(worktree)`; conflito → `CARD_BLOCKED` (ator `Factory("validate")`, `reason: merge_conflict`), sem revisão → `ValidateGates.evaluate` (build e teste de cada camada tocada via `GateExecutor`, `ScopeGate`, `SpecGates`; cada um grava `GATE_FINISHED`) → sessão MCP (papel `validate`) + `Runtime.launch(SessionRequest(stage="validate", ...))` + `RunCostRecorder.record` → lê o último `AGENT_REPORTED` posterior ao início da sessão → `ValidationOutcome.apply(cardId, report, GatesState)`.
- Decisão por resultado: `Staged` → fim; `Returned` → fim (o card volta a `CODING`, camada e motivo no evento); `Held` (APPROVED com gate vermelho) → `CARD_BLOCKED` com `reason: gates_failed: <nomes>`; `Malformed` ou sem relato → nova rodada (os gates reexecutam), até `maxRounds` (padrão 3, injetável), depois `CARD_BLOCKED` com `reason: review_malformed`. O relato do agente nunca decide sozinho (regra 8).
- `BranchSync` (interface em `WorktreeGit.kt`, implementação `SystemBranchSync` com `git fetch origin` + `git merge --no-edit origin/main` sem shell; conflito → `git merge --abort` e resultado `Conflict`; qualquer outra falha do git (fetch sem rede ou credencial, identidade de commit ausente, saída não zero) → resultado `Failed`, sem lançar exceção; os `git` rodam com `-c core.hooksPath=/dev/null -c core.fsmonitor=false` e identidade de commit fixa da fábrica, para nenhum hook ou config do repositório executar no processo do daemon): fake nos testes. `Failed` bloqueia o card (`CARD_BLOCKED`, `reason: git_failed`) sem rodar a revisão: sem isso a exceção deixaria o card em Validation e o dispatcher o relançaria em laço.
- `ValidateStageLauncher` (mesmo arquivo do pipeline): implementa `StageLauncher` para `DispatchStage.VALIDATE` com a mesma mecânica de thread, `onRunFinished` e `RunPool.release` em `finally` do `RefineStageLauncher`; outro estágio lança `IllegalArgumentException`. Config do MCP escrita fora do worktree (0600) e apagada com `sessions.close(token)` em `finally`, como no refine.
- `StageRouter` (`Dispatcher.kt`): `StageLauncher` que delega por `DispatchStage` a um `Map<DispatchStage, StageLauncher>`; estágio sem launcher lança `IllegalArgumentException`.
- `RefineRuntime.kt`: monta `ValidatePipeline` e `ValidateStageLauncher`, usa `StageRouter` e `stages = setOf(REFINE, VALIDATE)`. Develop (#40) acrescenta o seu estágio na mesma montagem.
- Sem migração; sem mudança em `core`, `store`, `ports` ou `adapters`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- `ValidatePipelineTest.kt` é arquivo novo: nenhuma suíte cobre o orquestrador do estágio; usa `FakeRuntime`, `BranchSync` falso e `Clock` fixo.
- Falha de git que não é conflito também bloqueia (`git_failed`): lançar exceção relançaria o card em laço (achado do auditor, corrigido na spec).
- Conflito com a main bloqueia o card em vez de devolver ao Coding: resolver conflito é da fase 3 (deploy); bloquear dá ao dono o motivo sem laço.
- Relato malformado repete a rodada (até 3) em vez de bloquear de imediato: é falha provável e barata de corrigir; esgotado, bloqueia.
- `Held` bloqueia (não só mantém): sem isso o card ficaria em Validation sendo redespachado em laço.
- Merge da main feito pelo daemon com `git` sem shell, não pela skill: a skill não tem shell (regra `no-shell` do #38).
- A montagem em `RefineRuntime` pode conflitar com a do #40 (develop); a ordem de merge resolve, sem decisão do dono.
- Peso `standard`: uma camada, 6 ACs, 4 arquivos, sem UI nem segurança.

## Open questions
- none

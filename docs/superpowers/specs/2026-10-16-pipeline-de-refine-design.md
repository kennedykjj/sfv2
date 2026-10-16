# Pipeline de refine — spec

**Issue:** #39 · **Branch:** `feat/39-pipeline-de-refine` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** Hoje dispatcher, runtime, skill de refine e gates existem separados; nada os liga. Um card em Ready for Refinement não avança sozinho até a spec ficar pronta para o dono revisar.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, igualmente, o que trabalha numa empresa): quer que a fábrica refine em segundo plano e só aparecer em Spec Review.
**Acceptance criteria**
- **AC-1** — Dado um card em Ready for Refinement e uma vaga de execução, quando o dispatcher roda, então o card passa a Refinement e um run da skill de refine é iniciado, com os eventos registrados com ator.
- **AC-2** — Dado um card em Refinement cujo run terminou e cujos gates estão todos verdes, quando o resultado é avaliado, então o card move para Spec Review.
- **AC-3** — Dado que o relato do agente diz "pronto" mas algum gate está vermelho, quando o resultado é avaliado, então o card não avança para Spec Review.
- **AC-4** — Dado gates vermelhos após um run, e rodadas restantes abaixo do máximo, quando o resultado é avaliado, então um novo run de refine é feito com o resultado dos gates como entrada.
- **AC-5** — Dado gates ainda vermelhos depois do máximo de rodadas, quando a última rodada termina, então o card fica bloqueado com um motivo legível para o dono (quais gates falharam) e nenhum novo run é iniciado.
- **AC-6** — Dado um card em Ready for Refinement sem vaga de execução, ou um card bloqueado ou em Spec Review, quando o dispatcher roda, então nenhum run de refine é iniciado para ele.
- **AC-7** — Dado um run de refine, quando a sessão MCP é aberta, então o arquivo de configuração do MCP (que carrega o token da sessão) é escrito FORA do worktree do card, com permissão 0600, o token nunca aparece em log nem em evento, e ao fim do run (sucesso, falha ou exceção) o arquivo é apagado e a sessão é revogada (`McpSessions.close(token)`); nenhum `mcp-config` pode aparecer no diff do card.
- **AC-8** — Dado um run de refine que termina por exceção ou por timeout, quando o `RefineStageLauncher` encerra a thread, então o lock do card no `RunPool` é solto (`finally`) e o card não fica preso para sempre.
**Out of scope:** Aprovação da spec e estágios seguintes (Ready for Dev em diante); conteúdo e qualidade da skill de refine e dos gates em si; UI/app; definição do valor do máximo de rodadas (vem da configuração existente); desbloqueio do card pelo dono.
**Product fit:** Serve "gates são scripts, não a palavra do agente" e "o dono aparece só onde a decisão é dele" (Spec Review). Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Só o módulo `daemon`; nenhum contrato externo (socket, MCP, eventos) muda.

- `daemon/src/main/kotlin/sfv2/daemon/RefinePipeline.kt` (novo): `RefinePipeline.run(cardId)` executa uma passagem do estágio. Fluxo: move o card para `REFINEMENT` (ator `Factory("refine")`) → `WorktreeManager.ensure` → abre sessão MCP (`McpSessions.open`, papel `refine`) → `Runtime.launch(SessionRequest(stage="refine", ...))` → `RunCostRecorder.record` → gates sobre a worktree: `SpecGates.check` (test-plan e weight) e `ScopeGate.check` (só spec/plano), via `GateExecutor`/eventos `GATE_FINISHED` → todos verdes: move para `SPEC_REVIEW`; algum vermelho: nova rodada, até `maxRounds`; esgotadas: evento `CARD_BLOCKED` (ator `Factory("refine")`) com payload `reason` listando os gates que falharam. O relato `completion_report` do agente não decide nada (regra 8).
- Entrada das rodadas seguintes: os `GATE_FINISHED` da rodada anterior ficam no log de eventos do card; a skill os lê pelas ferramentas MCP de leitura existentes. Nenhuma ferramenta MCP nova.
- `RefineStageLauncher` (mesmo arquivo): implementa `StageLauncher`; para `DispatchStage.REFINE` inicia `RefinePipeline.run` numa thread própria e devolve `LaunchedRun` do processo do daemon; ao terminar a thread chama `RunPool.release(cardId)`. Qualquer outro estágio lança `IllegalArgumentException`.
- `Dispatcher.kt`: novo parâmetro `stages: Set<DispatchStage> = DispatchStage.entries.toSet()`; estágios fora do conjunto são ignorados no ciclo (develop e validate ainda não têm pipeline).
- `Main.kt`: monta `Dispatcher` com `stages = setOf(REFINE)` e `RefineStageLauncher`, e o inicia junto com o socket.
- Máximo de rodadas: parâmetro `maxRounds` do `RefinePipeline` (padrão 3); sem leitura de configuração nova.
- Sem migração de banco; sem mudança em `core`, `store`, `ports` ou `adapters`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |

## Security
_Revisão do dono interino_
- **O token da sessão MCP é o segredo mais sensível do run:** vive só no arquivo de configuração do run (fora do worktree, 0600), na memória do daemon e no cabeçalho do agente; some quando o run acaba (AC-7). Dentro do worktree ele poderia ser commitado pelo próprio agente.
- **O relato do agente não decide nada:** quem move o card são os gates e o daemon (regra 8); o agente só vê texto não confiável como dado (skill do #36).
- **Laço de rodadas limitado** (`maxRounds`) e custo limitado pelo `LaunchGuard` (#34): um refine que nunca fica verde bloqueia o card com motivo para o dono.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- AC-6 da issue-origem e a do PO foram unidas (sem vaga, bloqueado, Spec Review): é um só comportamento do dispatcher, e mantém a spec em 6 ACs.
- `RefinePipelineTest.kt` é arquivo novo: nenhuma suíte do daemon cobre o orquestrador de um estágio; usa `FakeRuntime` e `Clock` fixo.
- Máximo de rodadas fixo em 3, injetável: o dono não pediu configuração e o valor é trivial de mudar depois.
- Entrada da rodada seguinte via eventos `GATE_FINISHED` já legíveis, em vez de ferramenta MCP nova: evita ampliar a superfície do MCP.
- Develop e validate ficam fora do dispatcher ligado (`stages`): suas issues (#40, #41) fazem a ligação.
- Peso `standard`: uma camada, 6 ACs, 5 arquivos, sem UI nem segurança.

## Open questions
- none

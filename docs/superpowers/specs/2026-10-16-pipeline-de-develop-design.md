# Pipeline de develop — spec

**Issue:** #40 · **Branch:** `feat/40-pipeline-de-develop` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Depois que o dono aprova a spec, nada leva o card de Ready for Dev até a validação: dispatcher, runtime, skills e gates existem separados. Sem isso o dono teria de acompanhar a implementação.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, igualmente, o que trabalha numa empresa): quer aprovar a spec, deixar a fábrica codar em segundo plano e só voltar quando houver resultado ou um bloqueio explicado.
**Acceptance criteria**
- **AC-1** — Dado um card em Ready for Dev e uma vaga de execução, quando o dispatcher roda, então o card passa a Coding e a etapa de develop é iniciada (QA escreve os testes vermelhos, depois os engenheiros por camada), com os eventos registrados com ator.
- **AC-2** — Dado um card em Coding cujo run terminou e cujos gates estão todos verdes, quando o resultado é avaliado, então o card move para Validation.
- **AC-3** — Dado que o relato do agente diz "pronto" mas algum gate está vermelho, quando o resultado é avaliado, então o card não avança para Validation.
- **AC-4** — Dado gate vermelho e rodadas restantes abaixo do máximo, quando a próxima rodada começa, então ela usa o modelo do degrau seguinte do step-up (`ModelStepUp`) e o motivo (quais gates falharam) fica registrado no card.
- **AC-5** — Dado gates ainda vermelhos depois do máximo de rodadas, quando a última rodada termina, então o card fica bloqueado com motivo legível para o dono (quais gates falharam) e nenhum novo run é iniciado.
- **AC-6** — Dado um card em Ready for Dev sem vaga de execução, ou um card bloqueado ou em Validation, quando o dispatcher roda, então nenhum run de develop é iniciado para ele.
- **AC-7** — Dado um run de develop, quando a sessão MCP é aberta, então o arquivo de configuração do MCP (com o token da sessão) é escrito FORA do worktree do card, com permissão 0600, o token nunca aparece em log nem em evento, e ao fim do run (sucesso, falha ou exceção) o arquivo é apagado e a sessão é revogada; nenhum `mcp-config` aparece no diff do card.
- **AC-8** — Dado um run de develop que termina por exceção ou timeout, quando a thread é encerrada, então o lock do card no `RunPool` é solto (`finally`) e o card não fica preso.
**Out of scope:** Estágio de Validation e seguintes (#41 em diante); conteúdo e qualidade das skills de QA/engenharia e dos gates em si; UI/app; valor do máximo de rodadas e da escada de modelos (vêm da configuração existente); desbloqueio do card pelo dono.
**Product fit:** Serve "gates são scripts, não a palavra do agente" (só gate verde move o card) e "o dono aparece só onde a decisão é dele" (nada de intervenção entre Ready for Dev e Validation, salvo bloqueio explicado). Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Só o módulo `daemon`; nenhum contrato externo (socket, MCP, eventos) muda.

- `daemon/src/main/kotlin/sfv2/daemon/DevelopPipeline.kt` (novo): `DevelopPipeline.run(cardId)` executa o estágio. Fluxo: move `READY_FOR_DEV` → `CODING` (ator `Factory("develop")`) → `WorktreeManager.ensure` → por rodada: sessão do QA (`stage="develop"`, papel `qa`; sessão MCP aberta e fechada como no refine) → gate vermelho (`GateCheck.Red`) → uma sessão por camada da spec (papel `engineer`) → gates verdes por camada (`GateCheck.Green`), `AcceptanceEditGate` e `ScopeGate` → `RunCostRecorder.record` a cada sessão. Todos verdes: move para `VALIDATION`. Algum vermelho: nova rodada até `maxRounds`; esgotadas: `CARD_BLOCKED` (ator `Factory("develop")`) com `reason` listando os gates que falharam. O `completion_report` do agente não decide nada (regra 8).
- Step-up (AC-4): o modelo de cada sessão vem de `ModelStepUp.modelFor(cardId)`, lido no início de cada rodada. O motivo de cada gate vermelho já fica no card pelo `GATE_FINISHED` (`gate`, `reason`, `summary`); o pipeline não cria evento novo para isso.
- Gates de camada: `DevelopGates` (interface, como `RefineGates`) com implementação que monta os `GateSpec` de teste e build de cada camada a partir de `ProjectLayer`, que ganha `build: List<String>? = null`, `test: List<String>? = null` e `ran: String? = null` lidos de `factory.yml` por `ProjectRegistry` (campos já existem no arquivo; hoje ignorados). Camada sem `test` falha o gate com motivo `no_test_command`. Os comandos vêm do `factory.yml` da `main` (nunca do worktree) e rodam sem shell: o texto é dividido em tokens por espaço, sem aspas nem expansão. O marcador `{selector}` (convenção do `factory.yml` atual) é trocado pelo nome simples de cada classe de teste da camada, tirado da tabela Test plan da spec (nome do arquivo sem `.kt`; só `[A-Za-z0-9_]` é aceito, outro valor falha o gate com `invalid_selector`); com mais de uma classe, o token anterior (`--tests`) e o marcador se repetem por classe. Sem tratar o marcador, o gate verde passaria `{selector}` literal ao Gradle (nenhum teste encontrado) e nenhum card sairia de Coding.
- `DevelopStageLauncher` (mesmo arquivo): implementa `StageLauncher`, igual ao `RefineStageLauncher`: thread própria, `RunPool.release(cardId)` no `finally`; qualquer outro estágio lança `IllegalArgumentException`. A espera do lock e o gancho `onRunFinished` seguem o mesmo contrato. A parte comum (thread, lock, finally) vira uma classe `PipelineStageLauncher` extraída de `RefineStageLauncher`, sem mudar o comportamento dele.
- `RefineRuntime.kt`: monta também o `DevelopStageLauncher`, um `ModelStepUp` (escada de modelos fixa na classe; ouvinte registrado no `EventStream`, fechado em `close()`), e passa `stages = setOf(REFINE, DEVELOP)` ao `Dispatcher` com um launcher que despacha por estágio.
- Máximo de rodadas: parâmetro `maxRounds` (padrão 3), sem leitura de configuração nova.
- Sem migração de banco; sem mudança em `core`, `store`, `ports` ou `adapters`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- `DevelopPipelineTest.kt` é arquivo novo: nenhuma suíte do daemon cobre o orquestrador do estágio; usa `FakeRuntime`, runner de gate falso e `Clock` fixo.
- AC-6 vai em `DispatcherTest.kt` (já cobre as regras de vaga/bloqueio do dispatcher) com `DispatchStage.DEVELOP`.
- Comandos de build/teste por camada entram em `ProjectLayer` (opcionais) em vez de arquivo de config novo: o `factory.yml` já os traz.
- Lógica de thread/lock extraída do `RefineStageLauncher` para reaproveitar: duplicar o `finally` do AC-8 arriscaria divergir.
- Máximo de rodadas fixo em 3 e escada de modelos fixa: o dono não pediu configuração; trivial de mudar depois.
- O motivo no card é o `GATE_FINISHED` existente, sem evento novo: evita ampliar o contrato de eventos.
- Peso `heavy`: mais de 6 ACs (8), mesmo com uma só camada e sem UI nem segurança.

## Open questions
- none

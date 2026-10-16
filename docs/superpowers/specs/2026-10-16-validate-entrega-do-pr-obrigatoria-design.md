# Validate: entrega do PR obrigatória, sem Staged sem CI por omissão — spec

**Issue:** #174 · **Branch:** `feat/174-validate-entrega-do-pr-obrigatoria` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** `ValidatePipeline` aceita `delivery: PullRequestDelivery? = null` e, sem entrega, `deliver()` devolve `CiResult.Green`. Uma montagem nova que esqueça o parâmetro manda o card a Staged sem CI.

**Audiência:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa: ambos confiam que Staged significa CI verde.

**Critérios de aceitação**
- AC-1: Dado um `ValidatePipeline` construído sem `PullRequestDelivery`, quando o código é compilado, então a construção é rejeitada (parâmetro obrigatório, sem valor padrão nulo).
- AC-2: Dado qualquer montagem do `ValidatePipeline`, quando a aprovação de Staged é avaliada, então ela sempre passa pela entrega e pelo resultado do CI; não existe caminho que devolva verde sem consultar o CI.
- AC-3: Dado um CI que não está verde ou uma forge indisponível (`UnavailableForge`), quando o validate chega à entrega, então o card não vai a Staged (comportamento atual de produção preservado).
- AC-4: Dado os testes dos estágios anteriores do validate, quando rodam, então passam um `FakeForge` com CI verde roteirizado e continuam verdes, sem depender do padrão nulo.

**Fora de escopo:** mudar o fluxo de entrega do PR, a espera pelo CI ou o modo autônomo; novas funções da forge; qualquer UI.

**Encaixe no produto:** serve "gates são scripts, não a palavra do agente": só um CI verde real move o card a Staged. Não contradiz posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contrato:** construtor de `ValidatePipeline` (`daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt`): `delivery: PullRequestDelivery` passa a obrigatório, sem padrão, posicionado logo após `model` (antes de `maxRounds`). Nenhum endpoint ou evento muda.
- **`ValidatePipeline.kt`:** `deliver()` chama `delivery.deliver(...)` direto (some o `?: CiResult.Green` e o KDoc "without a delivery wired"); `head = { delivery.headOf(worktree.path) }` sem `?.`.
- **Chamadores de produção:** `RefineRuntime.kt:133` já passa a entrega (`ProjectDelivery.create`); nenhuma mudança.
- **Testes a atualizar (só construção):** `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` (helper `pipeline(...)` e o teste de `maxRounds` padrão), `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` (`defaultRoundsOfEachPipeline`) e `e2e/src/test/kotlin/sfv2/e2e/PhaseExitTest.kt` passam um `PullRequestDelivery` sobre `FakeForge` com `scriptChecks(head, ChecksState.Passed)`. Em `ValidatePipelineTest` o helper `pipeline` usa por padrão uma entrega com CI verde roteirizado (`delivery(RecordingPublisher())`) em vez de `null`.
- **Camadas:** `daemon` (produção e testes) e `e2e` (apenas o ajuste de construção em teste). Sem migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- AC-1 é garantia de compilação: a prova é o módulo `daemon` (e o `e2e`) compilarem só com `delivery` explícito; o teste de AC-1 afirma por reflexão Java que o campo `delivery` de `ValidatePipeline` é do tipo `PullRequestDelivery` e que o construtor público sem ele não existe.
- Peso `standard`: 4 arquivos em Architecture (mais que 3), embora seja uma camada de produção.
- Sem arquivo de teste novo: `ValidatePipelineTest` já cobre o módulo; o ajuste em `DevelopPipelineTest` e `PhaseExitTest` é só de construção.
- `delivery` obrigatório vai após `model` para manter a ordem posicional dos chamadores existentes.

## Open questions
- none

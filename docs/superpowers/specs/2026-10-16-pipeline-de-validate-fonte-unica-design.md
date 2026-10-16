# Pipeline de validate: fonte única de maxRounds e conversão dos gates — spec

**Issue:** #162 · **Branch:** `feat/162-pipeline-de-validate-fonte-unica` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** No pipeline de validate (#41), o limite de rodadas (`maxRounds`) é um literal 3 repetido como parâmetro padrão, e a conversão dos resultados dos gates para o estado e para os nomes em `gates_failed` está espalhada entre `ProjectValidateGates` e `ValidatePipeline`. Duas fontes para a mesma regra divergem com o tempo e quebram o que move o card: o veredito dos gates.
**Audience:** O desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa, que dependem de o gate verde ou vermelho ser reportado de forma consistente.
**Acceptance criteria**
- **AC-1** — Given o `ValidatePipeline`, when ele é construído sem informar o limite de rodadas, then usa uma única constante documentada (não há configuração) como `maxRounds`, sem literal 3 duplicado no pipeline de validate.
- **AC-2** — Given um pipeline com `maxRounds` = N, when os gates seguem reprovando, then o pipeline para após N rodadas e reporta a falha.
- **AC-3** — Given os resultados de `GateExecutor`, `ScopeGate` e `SpecGates`, when convertidos, then um único tipo produz o `GatesState` correspondente.
- **AC-4** — Given um conjunto de resultados com falhas, when convertidos pelo mesmo tipo, then ele produz a lista de nomes dos gates reprovados usada em `gates_failed: <nomes>`.
- **AC-5** — Given os mesmos resultados dos gates antes e depois da mudança, when o pipeline de validate roda, then o `GatesState` e o `gates_failed` observados são idênticos (sem mudança de comportamento).
**Out of scope:** `maxRounds` em `DevelopPipeline` e `RefinePipeline`; criar configuração para `maxRounds`; alterar a spec do #41; mudar regras dos gates.
**Product fit:** Serve "gates são scripts, não a palavra do agente": o veredito dos gates passa a ter uma só tradução. Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Só o módulo `daemon`; nenhum contrato externo (socket, MCP, eventos) muda. O payload de `CARD_BLOCKED` (`gates_failed: <nomes>`) e os `GATE_FINISHED` ficam idênticos.

- `daemon/src/main/kotlin/sfv2/daemon/ValidateGateResults.kt` (novo): `class ValidateGateResults(val results: List<GateResult>)` com `state: GatesState` (`GREEN` se nenhum falhou, senão `RED`) e `failedNames: List<String>`; fábricas `fromExecutor(name, GateVerdict)`, `fromScope(ScopeVerdict)` e `fromSpec(SpecCheck)` (um `GateResult` por `SpecGate`) que concentram as conversões hoje privadas em `ProjectValidateGates`. Também a constante `const val DEFAULT_VALIDATE_MAX_ROUNDS = 3`, com KDoc: não há configuração de rodadas no projeto; o valor é um padrão da fábrica, injetável por parâmetro.
- `ValidateGates.evaluate` passa a devolver `ValidateGateResults` (em vez de `List<GateResult>`).
- `ProjectValidateGates.kt`: troca `layerGate`, `scopeGate` e `specGates` para usar as fábricas; monta o `ValidateGateResults`. Gravação de `GATE_FINISHED` inalterada.
- `ValidatePipeline.kt`: `maxRounds` padrão vira `DEFAULT_VALIDATE_MAX_ROUNDS`; `state` e o motivo `gates_failed: ` + `failedNames.joinToString(",")` vêm do tipo, sem `filter`/`if` locais.
- `DevelopPipeline` e `RefinePipeline` não mudam (fora de escopo). Sem migração; sem mudança em outras camadas.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- `maxRounds` é constante documentada, não configuração: nenhuma configuração de rodadas existe e criá-la está fora de escopo da issue.
- A constante vale só para o validate; Develop e Refine mantêm seu 3 (fora de escopo, mudança separada).
- Os testes entram em `ValidatePipelineTest.kt` (já cobre o pipeline, sem `ValidateGatesTest`): o pipeline com gates reais ou falsos exercita estado e nomes; sem suíte nova.
- Peso `standard`: uma camada, 5 ACs, 4 arquivos (3 de código e o teste), sem UI nem segurança.

## Open questions
- none

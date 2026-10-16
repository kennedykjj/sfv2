# Skill de validate via MCP — spec

**Issue:** #38 · **Branch:** `feat/38-skill-de-validate-via-mcp` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o estágio de validate (rerodar os gates e revisar o trabalho do Coding) precisa de uma skill que fale com a fábrica só por MCP, como as skills de refine e coding (#36/#37). Sem isso, o agente de validate improvisa fora do contrato e a revisão não vira um resultado que o daemon possa agir.

**Audiência:** o desenvolvedor sozinho com muitos projetos (e o que está numa empresa): quer que a revisão rode sem ele e que um bloqueio volte ao responsável, com motivo, sem ele intermediar.

**Critérios de aceitação**
- AC-1: Dado o texto da skill de validate, quando o teste de contrato lista as ferramentas MCP que ela cita, então todas existem no servidor MCP (card_get, spec_get, review_criteria_get, policies_get, layers_get, completion_report, file_write_request) e nenhuma outra aparece.
- AC-2: Dado a skill de validate, quando ela é lida, então não instrui mover o card nem rodar gate por conta própria: o resultado é entregue por completion_report e quem move o card é o daemon.
- AC-3: Dado o runtime falso e uma revisão que reporta um achado bloqueante, quando o estágio de validate termina, então o card volta ao dono do problema (o estágio que o originou) e não avança para Staged.
- AC-4: Dado o retorno do AC-3, quando se consulta o card, então o motivo do bloqueio (o achado) está registrado no evento de devolução.
- AC-5: Dado o runtime falso e uma revisão sem bloqueio, quando o validate termina e os gates estão verdes, então o card segue o caminho normal do pipeline (Staged).

**Fora do escopo:** abrir o PR (fase 3); novas ferramentas MCP; mover card por agente; rodar gates pela skill (o daemon roda).

**Aderência ao produto:** serve "gates são scripts, não a palavra do agente": o agente só relata a revisão e o daemon decide o movimento. Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:** nenhuma ferramenta MCP nova. A skill cita só as sete existentes. O veredito vai no `summary` de `completion_report`: primeira linha `VERDICT: APPROVED` ou `VERDICT: BLOCKED layer=<nome da camada>`, e o motivo nas linhas seguintes.
- `ValidationOutcome.apply(cardId, report, gates)` (daemon, novo): `report` é o texto do relato, `gates` é o enum `GatesState { GREEN, RED }`. Devolve `ValidationDecision`: `Returned(layer, reason)`, `Staged`, `Held` ou `Malformed`.
- `BLOCKED` → move VALIDATION→CODING com ator `Factory("validate")`; o payload do `CARD_MOVED` leva `layer` e `reason`. `APPROVED` com gates `GREEN` → VALIDATION→STAGED. `APPROVED` com gates `RED` → `Held`, o card fica. Relato fora do formato → `Malformed`, o card fica e nada é gravado.
- Toda gravação passa por `EventRecorder.record`; o movimento usa `MoveRules.check` do core, sem regra nova.

**Camadas e arquivos (daemon):**
- `daemon/src/main/resources/skills/validate.md` (novo): texto da skill, em português, falando só MCP.
- `daemon/src/main/kotlin/sfv2/daemon/ValidationOutcome.kt` (novo).
- `daemon/src/test/kotlin/sfv2/daemon/ValidateSkillTest.kt` (novo): contrato da skill contra as ferramentas registradas por `McpServerFactory.serverFor`.
- `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` (novo): fluxo com `FakeRuntime` + `McpRequestTools.completionReport`.

**Migração:** nenhuma. Aditivo.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidateSkillTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidateSkillTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidationOutcomeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada, mas mais de 3 ACs e mais de 3 arquivos.
- Dono do problema = coluna Coding (único retorno que `MoveRules` permite a partir de Validation); a camada citada no relato vai no payload para o pipeline #41 escolher o engenheiro.
- Sem ferramenta MCP nova (fora do escopo do PO): o veredito viaja no `summary` de `completion_report` com formato fixo, validado pelo daemon; o texto do agente é não confiável e só é lido pela primeira linha.
- Quem move o card é o daemon (`Factory`), nunca o agente: mantém "gate é script, não a palavra do agente".
- Arquivos de teste novos: nenhuma suíte existente cobre a skill nem o veredito de validate.
- A skill não roda gates: o daemon os roda e passa `GatesState` ao `ValidationOutcome`.

## Open questions
- none

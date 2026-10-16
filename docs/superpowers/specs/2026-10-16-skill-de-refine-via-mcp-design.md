# Skill de refine via MCP — spec

**Issue:** #36 · **Branch:** `feat/36-skill-de-refine-via-mcp` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** As instruções do estágio de refinamento são parte do produto: se citam uma ferramenta que o servidor MCP não oferece, o agente trava ou improvisa, e hoje não há teste que pegue isso.
**Audience:** o dono da fábrica (confia que o refine produz spec válida) e o agente de refine do runtime (lê a skill como única instrução do estágio).
**Acceptance criteria**
- **AC-1** — Given a skill de refine empacotada no daemon, when o teste de contrato compara as ferramentas que ela cita com as registradas pelo servidor MCP, then todas existem; citar uma ferramenta inexistente falha o teste.
- **AC-2** — Given o runtime falso roteirizado com a skill de refine, when ele produz a spec do card, then a spec segue o formato do template e não gera achado nos gates de spec (plano de teste e peso).
**Out of scope:** skills de develop, validate e deploy (#37, #38); o pipeline que dispara a skill (#39); ferramentas MCP novas.
**Product fit:** transforma o texto base da V0 em instrução do estágio falando MCP e coberta por teste, passo do roadmap da fase 2 (S2-18).
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada `daemon`. Nenhum contrato público muda: a skill só pode citar as ferramentas já registradas em `McpServerFactory.serverFor` (`card_get`, `spec_get`, `review_criteria_get`, `policies_get`, `layers_get`, `completion_report`, `file_write_request`).
- `daemon/src/main/resources/skills/refine/SKILL.md`: texto da skill em português (cabeçalhos da spec em inglês), reescrito a partir de `skills/refine` da V0; passos: ler card, critérios e camadas pelo MCP, escrever a spec com `file_write_request`, encerrar com `completion_report`. Sem shell, `gh` nem `board.sh`.
- `daemon/src/main/resources/skills/refine/spec-template.md`: cópia do template de spec que a skill manda seguir (o teste compara com o que `SpecGates` lê).
- `daemon/src/main/kotlin/sfv2/daemon/StageSkills.kt`: carrega a skill do classpath por nome de estágio (`StageSkills.load("refine")`); falha com mensagem que diz o estágio quando o recurso não existe.
- Teste de contrato: extrai da skill os nomes entre crases no formato `snake_case` com `_get`/`_request`/`_report` e confere contra os nomes registrados em `serverFor(session)`.
- Teste de AC-2: um agente roteirizado de teste (classe privada no arquivo de teste, que usa o `FakeRuntime` de `ports` para o lançamento) preenche o template da skill e grava a spec por `McpRequestTools.fileWriteRequest`; o teste lê o arquivo gravado e roda `SpecGates.check`.
- Arquivos: 2 recursos e 1 `.kt` novos. Camadas por `layer-of`: todos em `daemon`.
- Migração: nenhuma.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Teste de AC-1 em `McpServerFactoryTest` e de AC-2 em `SpecGatesTest`: as suítes dos módulos testados já existem, então não há arquivo de teste novo.
- O `FakeRuntime` de `ports` só devolve códigos de saída e não escreve arquivos; o agente que escreve a spec fica no teste, para não ampliar o port além deste card.
- A skill mora em resources do daemon, como pede o card, e o loader é um objeto simples sem IO além do classpath.
- Peso `light` (uma camada, 2 critérios, 3 arquivos, sem UI nem segurança), abaixo do `standard` esperado na nota do card.
- Os bloqueios #29 (ferramentas de pedido) e #32 (gates de spec) já estão na main (`McpRequestTools`, `SpecGates`); nenhum contrato novo é exigido deles.

## Open questions
- none

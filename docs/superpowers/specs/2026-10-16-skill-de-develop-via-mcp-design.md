# Skill de develop via MCP — spec

**Issue:** #37 · **Branch:** `feat/37-skill-de-develop-via-mcp` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** Os agentes da coluna Coding precisam de uma skill que diga como desenvolver usando só o MCP do daemon: QA escreve os testes de aceitação falhando e os engenheiros implementam por camada. Sem ela, o agente improvisa ou cita ferramentas que não existem.
**Audience:** O desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de uma empresa), que espera que a fábrica implemente a spec aprovada sem supervisão.
**Acceptance criteria**
- **AC-1** — Given o texto da skill de develop, When o teste de contrato extrai os nomes de ferramentas MCP citados, Then todos pertencem ao conjunto registrado no servidor MCP (card_get, spec_get, review_criteria_get, policies_get, layers_get, completion_report, file_write_request) e o teste falha se a skill citar uma ferramenta inexistente.
- **AC-2** — Given a skill de develop carregada, When ela é lida, Then ela ordena: QA escreve testes de aceitação que falham (vermelho) antes de qualquer implementação, e cada engenheiro implementa só a sua camada (conforme layers_get).
- **AC-3** — Given o runtime fake e um card em Coding com spec aprovada, When a sequência vermelho (testes falham), implementação, verde (testes passam) é executada seguindo a skill, Then os eventos registrados saem na ordem esperada, cada um com seu ator.
- **AC-4** — Given a mesma sequência, When o agente termina, Then ele reporta via completion_report e escreve arquivos só por file_write_request; a skill não instrui nenhuma outra forma de escrita nem de mover o card.
**Out of scope:** Novas ferramentas MCP; o runtime real de agentes; os gates de build e teste em si (já existem); skills de outras colunas; UI.
**Product fit:** Serve "gates são scripts, não a palavra do agente" (o verde vem do gate, não do relato) e "toda ação é um evento com ator". Não contradiz o posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:** nenhuma ferramenta MCP nova. `McpServerFactory` passa a expor `TOOL_NAMES: Set<String>` com as 7 ferramentas registradas (fonte única, usada no registro e no teste de contrato).

**Camadas e arquivos (só `daemon`):**
- `daemon/src/main/resources/skills/develop.md` (novo): texto da skill em pt-BR, com front matter `steps:` (lista ordenada de `role` + `gate`: `qa`/vermelho, `engineer`/verde) e corpo que cita cada ferramenta MCP entre crases. Manda ler `card_get`, `spec_get`, `layers_get`; QA escreve só testes via `file_write_request`; cada engenheiro só a sua camada; fim com `completion_report`; nunca move o card nem escreve por outro meio (o verde vem do gate).
- `daemon/.../SkillCatalog.kt` (novo): carrega `skills/<nome>.md` do classpath, parseia `steps` e extrai os nomes de ferramenta citados (todo token em crases no formato `snake_case` com `_`; assim uma ferramenta inventada falha o contrato); texto ausente lança exceção de domínio com o nome da skill.
- `daemon/.../McpServerFactory.kt`: define `TOOL_NAMES` e o usa no registro.

**Migração:** nenhuma (aditivo; arquivo novo de recurso).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopSkillTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopSkillTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopSkillTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopSkillTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Teste novo `DevelopSkillTest.kt`: nenhuma suíte do daemon cobre skills; os ACs compartilham o arquivo.
- AC-3 em processo: `FakeRuntime` lança as sessões na ordem de `steps`; o "agente" é simulado chamando `McpRequestTools.completionReport` com a sessão do papel, e os gates vermelho/verde usam `GateExecutor` com runner falso; esperado `AGENT_REPORTED`(qa), `GATE_FINISHED`(vermelho), `AGENT_REPORTED`(engineer), `GATE_FINISHED`(verde). O pipeline real é o #40.
- Skill como recurso de classpath do daemon (não arquivo solto): o contrato com `TOOL_NAMES` é verificável em teste e vai no mesmo artefato do servidor.
- Peso standard: uma camada, 4 ACs, 3 arquivos, sem UI nem segurança.

## Open questions
- none

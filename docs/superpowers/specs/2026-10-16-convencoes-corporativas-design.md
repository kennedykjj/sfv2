# Convenções corporativas — spec

**Issue:** #90 · **Branch:** `feat/90-convencoes-corporativas` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** na empresa, commit, branch e PR precisam seguir o padrão da casa (chave do ticket no commit, nome de branch, template e idioma do PR). Hoje a fábrica aplica formato de commit e de branch, mas o commit não leva a chave do ticket do tracker e o PR não usa o template nem o idioma do projeto.

**Público:** o desenvolvedor dentro de uma empresa (Jira, convenções da empresa).

**Critérios de aceitação**
- AC-1: Dado um card importado de um ticket (ex.: PROJ-123) e `commit_format` com `{id}` no factory.yml, quando a fábrica commita, então a mensagem leva a chave do ticket (PROJ-123) no lugar indicado.
- AC-2: Dado `branch_format` no factory.yml, quando a fábrica cria a branch do card, então o nome segue o padrão (`{id}`, `{slug}`).
- AC-3: Dado um template de PR no repositório (lido da `main`), quando a fábrica abre o PR, então o corpo segue as seções do template.
- AC-4: Dado o idioma do projeto no factory.yml, quando a fábrica abre o PR, então título e corpo saem nesse idioma.
- AC-5: Dado convenção ausente ou inválida no factory.yml, quando a fábrica commita, cria branch ou abre PR, então usa o padrão da fábrica e registra o motivo, sem travar o card.
- AC-6: Dado um card sem ticket externo, quando a fábrica commita com `{id}` no formato, então usa o id do card, sem placeholder solto na mensagem.

**Fora de escopo:** criar ou editar convenções pela UI; validar convenções contra regras do servidor (hooks, branch protection); mudar commits/branches já criados; template de issue; atualizar o ticket no tracker.

**Aderência ao produto:** atende o público "desenvolvedor dentro de uma empresa" e a restrição de política explícita por projeto; não contradiz o posicionamento (nada novo toca o tracker, a chave vem do ExternalRef já importado).

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** `Conventions` (core) ganha `prTemplate: String` (chave `conventions.pr_template`, padrão `.github/pull_request_template.md`) e `language: String` (chave `conventions.language`, padrão `en`; valores `en` e `pt-BR`). Valor inválido segue o `InvalidPolicyException` existente (o registro rejeita só o campo e usa o padrão da fábrica, registrando o campo). `{id}` no `commit_format` passa a significar a chave do ticket (`ExternalRef.externalId` da primeira referência do card) e, sem referência, o id do card; `branch_format` não muda (AC-2 já é atendido por `WorktreeManager.branchName`).
- **Novo `ConventionText` (daemon):** funções puras `commitSubject(format, type, scope, subject, ticketOrCardId)`, `pullRequestTitle(...)` (usa o `commit_format` com `type=feat`) e `pullRequestBody(template, language, ticket)` (template da `main` verbatim com a linha `Ticket: <chave>` rotulada no idioma; sem template, corpo mínimo no idioma). Sem placeholder solto na saída.
- **Camadas e arquivos:** `core` — `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt`; `daemon` — `daemon/src/main/kotlin/sfv2/daemon/ConventionText.kt` (novo), `ValidatePipeline.kt` (monta título e corpo do PR no `deliver`, lendo o template via `ProjectFileSource`), `McpServerFactory.kt` e `Protocol.kt` (expõem `ticket`, `prTemplate` e `language` às skills para o agente commitar com a chave). `ProjectRegistry.POLICY_KEYS` já aceita `conventions`.
- **Migração:** nenhuma (config opcional; os padrões preservam o comportamento atual).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConventionTextTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-5 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConventionTextTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- `{id}` do commit vira a chave do ticket com fallback ao id do card: é o que o AC-1 pede e não quebra formatos existentes.
- Idiomas aceitos `en` e `pt-BR`, só para os rótulos fixos do PR; o título do card é dado e não é traduzido.
- Arquivo de teste novo `ConventionTextTest.kt`: nenhuma suite cobre a classe pura nova.
- Ticket no nome da branch fica fora: a branch é recalculada por `branchName(id, slug)` em vários pontos e mudar quebraria a reconstrução.
- Peso heavy: duas camadas (core e daemon).

## Open questions
- none

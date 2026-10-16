# Convenções corporativas — plano (#90)

Spec: `docs/superpowers/specs/2026-10-16-convencoes-corporativas-design.md`

## Tarefas

### T1 — core: `Conventions` ganha `prTemplate` e `language` (agent: engineer)
- Arquivo: `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt`.
- `Conventions(commitFormat, branchFormat, prTemplate, language)`; chaves `conventions.pr_template` (padrão `.github/pull_request_template.md`) e `conventions.language` (`en` | `pt-BR`, padrão `en`).
- `{ticket}` passa a ser placeholder válido do `commit_format` (`{id}` mantém o significado).
- Valor inválido: `InvalidPolicyException` com o campo.
- Dependências: nenhuma.

### T2 — daemon: `ConventionText` e uso no deliver (agent: engineer)
- Arquivos: `daemon/src/main/kotlin/sfv2/daemon/ConventionText.kt` (novo), `ValidatePipeline.kt`, `McpServerFactory.kt`, `Protocol.kt`.
- `commitSubject`, `pullRequestTitle`, `pullRequestBody`; chave do ticket validada (`^[A-Za-z0-9_-]{1,40}$`), fallback ao id do card.
- `deliver` lê o template da `main` via `ProjectFileSource` e monta título/corpo.
- Expor `ticket`, `prTemplate`, `language` no MCP/Protocol.
- Dependências: T1.

## Ordem
T1 → T2. QA escreve os testes de aceitação antes (core: `ProjectPoliciesTest`; daemon: `ConventionTextTest`, `WorktreeManagerTest`, `ValidatePipelineTest`).

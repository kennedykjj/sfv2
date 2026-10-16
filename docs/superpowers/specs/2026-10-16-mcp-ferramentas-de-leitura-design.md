# MCP: ferramentas de leitura — spec

**Issue:** #28 · **Branch:** `feat/28-mcp-ferramentas-de-leitura` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** agentes precisam ler o contexto do trabalho (card, spec, critérios, políticas, camadas) sem tocar o banco nem o tracker externo; o daemon é o único escritor e dono do estado.
**Público:** o desenvolvedor sozinho com muitos projetos (e, igualmente, o desenvolvedor em empresa), cujos agentes rodam em vários projetos na mesma máquina.

**Critérios de aceitação**
- AC-1: Dado um agente conectado ao servidor MCP, quando ele chama as ferramentas de leitura para um card do próprio projeto, então recebe o card, a spec, os critérios de revisão do projeto (`.shipfloor/review-criteria.md` em main), as políticas e as camadas do projeto.
- AC-2: Dado um agente de um projeto, quando ele pede a leitura de um card de outro projeto, então a leitura é recusada com erro que diz o que aconteceu e o id do card, sem devolver conteúdo.
- AC-3: Dado texto vindo de fora (issue, ticket, comentário) no card ou na spec, quando o agente o lê por qualquer ferramenta, então o texto chega marcado como não confiável, separado do conteúdo da própria fábrica.
- AC-4: Dado o servidor MCP, quando uma requisição chega sem o token de sessão no cabeçalho `Authorization: Bearer`, com token inválido, com `Origin` de navegador ou com `Host` diferente de `127.0.0.1:<porta>`, então é recusada antes de qualquer leitura, sem dizer qual das condições falhou e sem eco do token.
- AC-5: Dado uma ferramenta que lê arquivo ou spec acima de 256 KiB, quando é chamada, então o agente recebe erro com o id do card ou o nome do arquivo e nenhum conteúdo parcial.

**Fora de escopo**
- Ferramentas de escrita (mudar card, spec, eventos) e transição de colunas.
- Qualquer acesso ao tracker externo ou ao banco pelo agente.
- Autenticação além de identificar o projeto do agente conectado.
- Edição de critérios, políticas e camadas.

**Encaixe no produto:** serve "o daemon é o único escritor; agentes são clientes por MCP" e "texto externo entra por uma porta só, marcado como não confiável". Não contradiz o posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos (ferramentas MCP, todas somente leitura, sem argumento de projeto: o projeto vem da sessão):**
- `card_get(cardId)` → `{id, title: Untrusted, column, references: [{provider, externalId, url}]}`.
- `spec_get(cardId)` → `{cardId, spec: Untrusted}`; spec ausente ou divergente do hash → erro com o id do card.
- `review_criteria_get()` → `{criteria: Untrusted}`, lido de `.shipfloor/review-criteria.md` em `main`.
- `policies_get()` → `{deliveryMode, signers, conventions}`; `layers_get()` → `[{name, path}]` (de `ProjectRegistry`).
- `Untrusted` = `{trust: "untrusted", source: "card"|"spec"|"project-file", text}`. Todo texto livre sai nesse envelope; campos estruturados (política, camadas, coluna) saem sem ele.

**Camadas e arquivos:**
- `store`: migração `4.sqm` com a tabela `card_project(card_id PK, repo)`; `CardEventStore.assignProject(cardId, repo)` e `projectOf(cardId): String?` (consulta e escrita fora da transação de `recordChange`: quem preenche o vínculo é a importação, #56, e o dispatcher). Card sem vínculo é tratado como de outro projeto (falha fechada).
- `daemon`: `McpSession` (repo do projeto, fixado na abertura da sessão), `McpReadTools` (lógica das ferramentas, sem dependência do SDK, testável em processo), `McpServerFactory` (registra as ferramentas no servidor do SDK oficial de MCP em Kotlin, mapeando exceção de domínio para erro de ferramenta), `ProjectFileSource` (lê arquivo de `main` por `git show`, mesmo limite e timeout de `GitProjectConfigSource`, que passa a implementá-la), `UntrustedText`.
- `gradle/libs.versions.toml` + `daemon/build.gradle.kts`: dependência do SDK de MCP (e do transporte), versão fixa.
- Recusa: `McpReadTools` compara `store.projectOf(cardId)` com o repo da sessão antes de ler qualquer coisa; erro `card <id> does not belong to project <name>`, sem conteúdo; log `key=value` só com ids (`cmd`, `card`, `project`).
- Transporte: servidor MCP em loopback (`127.0.0.1`), token por sessão que o daemon emite ao abrir a sessão e que amarra o repo; token ausente ou inválido recusa a conexão. Quem abre a sessão e entrega o token ao agente é o dispatcher (#27), fora deste card; aqui a sessão se abre por `McpSessions.open(repo): token`.

**Migração:** expandir apenas (tabela nova, nada destrutivo); `## Rollback` n/a. Teste de abrir banco da versão 3 em `store/src/test/kotlin/sfv2/store/ModuleTest.kt`.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests; checked by `gate.sh test-plan`. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |

## Security
_Revisão do dono interino_
- **Primeira porta de um agente para o daemon:** o token de sessão vem de `SecureRandom` (32 bytes), é comparado em tempo constante, só vale para o repositório da sessão e nunca aparece em log, erro ou resposta. Ele não dá acesso ao `owner.token` nem a nenhum comando do socket de `card.*` ou `project.*`.
- **Servidor só em loopback**, no cabeçalho `Authorization` (nunca na URL), com checagem de `Host` e recusa de `Origin` presente: uma página aberta no navegador do dono não pode chamar o servidor (AC-4).
- **Ferramentas só leem e não têm argumento de projeto:** o projeto vem da sessão (sem confused deputy); card sem vínculo ou de outro projeto falha fechado.
- **Todo texto livre sai como `Untrusted`:** o agente trata o envelope como dado, nunca como instrução (ARCHITECTURE, regra 6); tamanho limitado (AC-5).
- **Registrar para #29:** as ferramentas de pedido nunca aceitam ator nem prova do dono; o MCP fala sempre como `agent:<papel>` (restrição do #115).

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- "Critérios" do AC-1 são os critérios de revisão do projeto (`.shipfloor/review-criteria.md`): aparece ao lado de políticas e camadas, que também são do projeto; os critérios de aceitação do card já vêm dentro da spec.
- Card passa a ter vínculo com projeto no store (`card_project`): hoje o card não guarda o projeto e sem isso o AC-2 não é verificável; sem vínculo, a leitura é recusada.
- Título do card, spec e arquivo de critérios saem sempre como `Untrusted`, sem tentar distinguir texto importado de texto da fábrica: a spec é escrita por agente a partir de texto externo, então classificar por origem seria falsa garantia (ARCHITECTURE regra 6).
- Todos os testes do plano ficam num arquivo novo `McpReadToolsTest.kt`: nenhuma suíte do `daemon` cobre MCP; usa fake de `ProjectConfigSource`/`ProjectFileSource` e banco em memória, e a lógica não depende do SDK.
- Transporte HTTP em loopback com token por sessão (e não stdio): o daemon é um processo único e o agente roda em outro; incerteza: o artefato e a versão exatos do SDK de MCP em Kotlin precisam ser confirmados na implementação (pinada em `libs.versions.toml`).
- Peso `heavy`: toca duas camadas (`store` e `daemon`).

## Open questions
- none

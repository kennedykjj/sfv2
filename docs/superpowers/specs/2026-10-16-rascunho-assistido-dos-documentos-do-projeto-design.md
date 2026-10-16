# Rascunho assistido dos documentos do projeto — spec

**Issue:** #83 · **Branch:** `feat/83-rascunho-assistido-dos-documentos-do-projeto` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O `sfv2 init` (#82) mostra quais dos documentos PRODUCT.md, ARCHITECTURE.md e DESIGN.md faltam, mas não ajuda a criá-los. O dono de um projeto novo começa do papel em branco, e sem esses documentos os agentes trabalham com menos contexto.
**Audience:** O desenvolvedor que acabou de registrar um repositório e quer um ponto de partida para os documentos de contexto, que ele revisa e commita à mão.
**Acceptance criteria**
- **AC-1** — Given um pedido `project.docs.draft` sem a prova do dono ou com prova inválida, when o daemon o recebe, then responde erro dizendo que só o dono, com prova válida, pede rascunhos, e nenhuma sessão de agente é lançada.
- **AC-2** — Given um projeto registrado em que PRODUCT.md e DESIGN.md faltam na `main` e ARCHITECTURE.md existe, when o dono pede o rascunho, then uma sessão de agente é lançada e a resposta traz um rascunho para cada documento ausente (PRODUCT.md e DESIGN.md) e nenhum para ARCHITECTURE.md.
- **AC-3** — Given um projeto com os três documentos na `main`, when o dono pede o rascunho, then a resposta vem com a lista de rascunhos vazia e nenhuma sessão de agente é lançada.
- **AC-4** — Given um rascunho em andamento, when a sessão roda, then ela roda num diretório temporário fora do repositório do dono, o repositório do dono não recebe nenhum arquivo, e o diretório temporário e o token da sessão são removidos ao fim, qualquer que seja o resultado.
- **AC-5** — Given uma sessão que termina com código diferente de zero, que fica pendurada (`Hung`) ou que não grava o arquivo de um documento pedido, when o daemon monta a resposta, then o documento sem rascunho sai marcado como falho com o motivo (`exit_code`, `hung` ou `not_written`), os demais rascunhos são mantidos e nada é gravado no repositório.
- **AC-6** — Given um rascunho pedido, when o daemon registra o que aconteceu, then o log estruturado traz só o nome do projeto, os nomes dos documentos e o resultado de cada um, nunca o texto lido do repositório nem o texto do rascunho.
**Out of scope:** Gravar o rascunho no repositório do dono ou abrir PR com ele (o dono revisa e commita); comando de CLI e tela do app que chamam o pedido (card próprio, camadas `cli` e `app`); registrar custo da sessão (o custo da fábrica é por card e o rascunho não tem card); rascunho de outros arquivos além dos três documentos.
**Product fit:** Fecha a ponta de onboarding da fase 8 (decisions.md, "mínimo em dois níveis"): o `init` mede, o rascunho ajuda a preencher. O dono continua decidindo, porque nada entra no repositório sem ele.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `daemon`.

**Arquivos:**
- novo `daemon/src/main/kotlin/sfv2/daemon/ProjectDocsDraft.kt`: `class ProjectDocsDraft(registry, files: ProjectFileSource, sessions: McpSessions, runtime: Runtime, ownerProof, scratchRoot, mcpPort, configDir, model, log)` com `handle(request): Response`.
- `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `Request.ProjectDocsDraft(actor, project, proof)` (`project.docs.draft`) e `Response.DocsDraftResult(drafts)`, cada item com `doc`, `status` (`drafted` ou `failed`), `text` ou `reason`.
- `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: roteia o pedido novo para `ProjectDocsDraft`.
- `daemon/src/main/kotlin/sfv2/daemon/McpRequestTools.kt`: o papel `docs-draft` só escreve em `docs-draft/<DOC>.md` dos três nomes fixos.
- `daemon/src/main/kotlin/sfv2/daemon/Module.kt`: monta o serviço com o runtime já configurado.
- novo `daemon/src/main/resources/skills/docs-draft/SKILL.md`: instrução do estágio.

**Fluxo:** (1) confere ator `Owner` e prova antes de qualquer leitura (igual a `ProjectCommands.add`); (2) acha o projeto no registro (um só registrado quando `project` vem nulo, como em `card.rebuild`); (3) lê os três documentos da `main` pelo `ProjectFileSource` e separa os ausentes; sem ausente, responde lista vazia; (4) cria um diretório temporário sob `scratchRoot`, exporta nele a árvore da `main` só para leitura de contexto, abre a sessão MCP com papel `docs-draft` e lança `runtime.launch(SessionRequest("docs-draft", scratch, model, config, prompt))`; (5) lê os arquivos gravados em `docs-draft/`, monta a resposta e apaga o diretório temporário, o arquivo de config MCP e a sessão num `finally`.

**Regras:** o repositório do dono nunca é cwd nem alvo de escrita da sessão. O daemon é o único que escreve, e só no diretório temporário. Nenhuma escrita no banco: sem card, não há estado nem evento; o pedido é um cálculo sem efeito persistente. Limite de tamanho de cada rascunho lido (`ProjectFileSource.MAX_BYTES`).

**Migração:** nenhuma; a forma dos pedidos e respostas existentes não muda, `project.docs.draft` é só um comando novo.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |

## Security
_Cybersec_

- O conteúdo do repositório (documentos existentes, código) é dado não confiável: o prompt da sessão diz isso e o texto lido nunca vira instrução; a resposta devolve o rascunho como texto para o dono revisar.
- Escrita da sessão confinada: só os três nomes fixos sob `docs-draft/` do diretório temporário; caminho com `..`, absoluto, link simbólico ou fora da lista é recusado pela validação já existente de `file_write_request`.
- O repositório do dono não é montado como cwd da sessão, então o agente não tem caminho para alterá-lo.
- O token da sessão e o arquivo de config MCP (modo 0600) são removidos no `finally`, mesmo em falha ou `Hung`.
- Log só com ids: nome do projeto e nomes dos documentos, nunca texto lido ou gerado.
- Sem efeito fora da máquina além da chamada ao runtime já aprovado; nada é publicado.

## Rollback
O comando é novo e aditivo. Reverter o commit remove o `project.docs.draft`, a skill e o papel `docs-draft`, sem migração e sem dado a desfazer.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- O rascunho é devolvido ao dono e não gravado no repositório: o #82 já fixou "nenhuma escrita no repositório do dono" e o dono revisa antes de commitar.
- Sem card, sem evento e sem custo registrado: a trilha da fábrica é por card (`RunCostRecorder` pede o id do card). Custo do rascunho fica para um card próprio, se o dono quiser; o log do daemon registra o resultado.
- Camada só `daemon`, como no roadmap (S8-06); CLI e app que chamam o pedido ficam para card próprio, para não passar de uma camada por entrega.
- Peso `heavy` por `## Security` e `## Rollback` com conteúdo, não pelo número de caminhos.
- Sem arquivo de teste novo: `ProjectRegistryTest.kt` é a suíte do domínio de projeto no `daemon`; o runtime e a fonte de arquivos entram pelos fakes das ports.
- A sessão roda num diretório temporário com a árvore da `main` exportada, e não no repositório do dono: o `Runtime` exige um `worktree`, e assim nenhuma escrita do agente alcança o repositório.
- Prova do dono exigida mesmo sendo um pedido sem efeito persistente: a sessão gasta tokens do dono e lê o repositório.

## Open questions
- none

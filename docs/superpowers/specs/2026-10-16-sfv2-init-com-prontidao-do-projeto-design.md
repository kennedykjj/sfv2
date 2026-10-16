# sfv2 init com prontidão do projeto — spec

**Issue:** #82 · **Branch:** `feat/82-sfv2-init-com-prontidao-do-projeto` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** Hoje registrar um projeto é `sfv2 project add`, que só devolve o erro do daemon quando algo falta. O dono novo não sabe o que o projeto precisa ter para a fábrica rodar, nem o quanto os documentos de contexto (PRODUCT, ARCHITECTURE, DESIGN) já estão prontos.
**Audience:** O desenvolvedor que registra um repositório na fábrica pela primeira vez (inclusive o de empresa, com convenções próprias).
**Acceptance criteria**
- **AC-1** — Given um diretório que não é repositório git, when o dono roda `sfv2 init <caminho>`, then o comando sai com código 1, a mensagem diz o que faltou e como resolver (`git init` ou o caminho certo), e nenhum pedido `project.add` é enviado ao daemon.
- **AC-2** — Given um repositório git sem `.shipfloor/factory.yml` na `main`, when o dono roda `sfv2 init <caminho>`, then o comando sai com código 1, a mensagem nomeia o arquivo e o branch `main` e diz que ele precisa estar commitado lá, e nenhum `project.add` é enviado.
- **AC-3** — Given um repositório com `factory.yml` na `main`, when o dono roda `sfv2 init <caminho>`, then o projeto é registrado no daemon com a prova do dono (`project.add`) e o comando imprime a linha do projeto e o score de prontidão `n/3` dos documentos PRODUCT.md, ARCHITECTURE.md e DESIGN.md lidos da `main`.
- **AC-4** — Given um projeto registrável sem um ou mais dos três documentos, when o dono roda `sfv2 init`, then o comando sai com código 0 e lista cada documento ausente como `ausente`, sem bloquear o registro.
- **AC-5** — Given um projeto já registrado, when o dono roda `sfv2 init <caminho>` de novo, then nenhum segundo `project.add` é enviado, o score é impresso e o código é 0.
- **AC-6** — Given um `factory.yml` que o daemon recusa (campo inválido), when o dono roda `sfv2 init`, then o comando sai com código 1 e mostra a recusa do daemon com o nome do campo.
**Out of scope:** Rascunho dos documentos ausentes (#83); exigir `build` e `test` por camada no `factory.yml` (a validação de conteúdo continua do daemon); portes macOS e Windows; qualquer escrita no repositório do dono.
**Product fit:** Cumpre o mínimo em dois níveis (decisions.md, proposta pendente): bloqueia só o verificável (git, `factory.yml`), documentos entram como score. Não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `cli`.

**Arquivos:**
- novo `cli/src/main/kotlin/sfv2/cli/InitCommand.kt`: `class InitCommand(cli: CliEnvironment)` (`sfv2 init [path]`, padrão `.`) e `fun Sfv2Command.withInitCommand(cli)`.
- `cli/src/main/kotlin/sfv2/cli/Main.kt`: encadeia `.withInitCommand(cli)`.
- `cli/src/test/kotlin/sfv2/cli/MainTest.kt`: testes novos na suíte existente (daemon falso já está lá).

**Fluxo:** (1) caminho canônico como em `ProjectAdd`; (2) `git -C <path> rev-parse --git-dir` via `cli.runCommand` (já com timeout de `runProcess`); falha = AC-1; (3) `git -C <path> show main:.shipfloor/factory.yml`; nulo = AC-2; (4) registro com a mesma sequência de `ProjectAdd` (`project.list`, depois `project.add` com `ownerToken()`); a lógica compartilhada sai de `ProjectAdd` para uma função interna de `ProjectCommands.kt` reaproveitada pelos dois, sem duplicar; (5) `git show main:<doc>` para PRODUCT.md, ARCHITECTURE.md e DESIGN.md; saída não vazia conta como presente.

**Regras:** leitura sempre da `main` (igual ao daemon), nunca da árvore de trabalho. Sem YAML no CLI: a validade do `factory.yml` é do daemon, e a recusa dele chega pela falha já tratada em `cli.call`. Sem acesso ao banco; sem regra de negócio nova fora do que o CLI já faz.

**Migração:** nenhuma; sem mudança de store, evento ou protocolo.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-2 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-3 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-4 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-5 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-6 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Os três documentos são só score e nunca bloqueiam: segue a proposta de "mínimo em dois níveis" de decisions.md; a proposta ainda espera o aceite do dono, então vale como padrão e se inverte trocando o código de saída.
- `build`/`test` por camada não são exigidos no `init`: conferir exigiria parsear YAML no CLI e duplicar a validação do daemon; fica para um card próprio se o dono quiser.
- Leitura pela `main` com `git show`, como o `GitProjectConfigSource`: o `init` avisa o mesmo que o daemon vai enxergar.
- Sem arquivo de teste novo: `MainTest.kt` já cobre os comandos do módulo `cli` e tem o daemon falso.
- Caminho vindo do argumento só vai a `git -C` sem shell e ao `project.add` canônico; o git roda com o timeout do `runProcess`.

## Open questions
- none

# Registro de projetos — spec

**Issue:** #20 · **Branch:** `feat/20-registro-de-projetos` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** a fábrica trabalha em vários projetos e cada um declara camadas e políticas no próprio repositório (`.shipfloor/factory.yml`). Hoje o daemon não conhece nenhum projeto, então não há onde ancorar cards nem políticas.
**Audience:** o desenvolvedor sozinho com muitos projetos (até dez numa máquina), que registra um repositório sem configurar board.
**Acceptance criteria**
- **AC-1** — Dado um repositório com `.shipfloor/factory.yml` válido na `main`, quando o projeto é registrado no daemon pelo socket, então ele passa a constar na listagem com nome, camadas e políticas.
- **AC-2** — Dado um `factory.yml` inválido na `main`, quando o registro é pedido, então o daemon recusa e a resposta aponta o campo inválido.
- **AC-3** — Dado um registro recusado, quando a listagem é consultada, então o projeto não consta.
- **AC-4** — Dado um projeto registrado e um `factory.yml` alterado numa branch (não mergeada), quando o daemon lê a configuração, então valem os valores da `main`.
- **AC-5** — Dado um repositório sem `.shipfloor/factory.yml` na `main`, quando o registro é pedido, então o daemon recusa com erro que diz que o arquivo não foi encontrado.
- **AC-6** — Dado um projeto já registrado, quando o mesmo repositório é registrado de novo, então o daemon recusa por duplicidade e a listagem segue com uma única entrada.
- **AC-7** — Dado um pedido `project.add` com ator agente, adaptador ou fábrica, ou com ator dono sem `proof` válida, quando o daemon o processa, então recusa com motivo e o projeto não é registrado: só o dono, com prova, registra projetos.
**Out of scope:** comandos `sfv2 project add/list` da CLI (card separado); persistência do registro (fica em memória, perdido ao reiniciar o daemon); recarregar a configuração após mudança na `main`; remoção de projetos; UI.
**Product fit:** serve ao princípio de a fábrica ser dona do próprio estado, com política por projeto declarada no repositório e lida da `main`, não de rascunho em branch. Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Camada:** só `daemon`. O `core` já tem `ProjectPolicies.from(Map)` (#19) e é reusado sem mudança.
- **Arquivos:** `daemon/build.gradle.kts` (dependência de YAML), `gradle/libs.versions.toml` (versão fixa), `daemon/src/main/kotlin/sfv2/daemon/ProjectRegistry.kt` (novo), `ProjectConfigSource.kt` (novo), `Protocol.kt`, `SocketServer.kt`, `Main.kt` e o teste do plano.
- **Leitura da `main`:** `interface ProjectConfigSource { fun read(repo: Path): String? }` (null = arquivo ausente). A implementação `GitProjectConfigSource` roda `git -C <repo> show main:.shipfloor/factory.yml` (processo sem shell, argumentos fixos, timeout, tamanho máximo do arquivo). Lê do commit, nunca da árvore de trabalho, então checkout ou edição em branch não muda nada.
- **Parse:** `org.snakeyaml:snakeyaml-engine` (modo seguro, sem tipos arbitrários) vira `Map<String, Any?>`; o resto é validação própria.
- **Contrato (`sfv2.daemon`):**
  - `data class ProjectLayer(name: String, path: String)`; `data class Project(name: String, repo: String, layers: List<ProjectLayer>, policies: ProjectPolicies)`.
  - `class ProjectRegistry(source: ProjectConfigSource)` com `register(repo: Path): Project` e `list(): List<Project>` (em memória, `@Synchronized`).
  - `class InvalidProjectConfigException(val field: String, message)` : `RuntimeException`; `ProjectNotFoundException`/`ProjectAlreadyRegisteredException` com o repositório na mensagem.
  - Campos exigidos: `project.name` (não vazio), `layers` (lista não vazia; cada item com `name` e `path` não vazios, nomes únicos). Falha de política repassa o campo de `InvalidPolicyException`.
- **Protocolo:** `project.add` (`actor`, `path`, `proof`): só o ator dono com `proof` válida registra (a mesma `OwnerProof` de `card.*`, verificada antes de ler qualquer arquivo ou rodar o git); `project.list` é leitura e dispensa prova e `project.list`; respostas `ProjectResult`/`ProjectsResult` com nome, camadas e políticas. Erro devolve `ok=false` com a mensagem que nomeia o campo.
- **Duplicidade:** chave é o caminho canônico do repositório (`toRealPath`).
- **Migração:** nenhuma (sem persistência).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |

## Security
_Revisão do dono interino_
- **Por que só o dono registra:** o `factory.yml` do projeto declara quem assina cada efeito externo (`signers`, #19), inclusive `policy` (dispensa a assinatura do dono). Quem registra um repositório escolhe, na prática, quais efeitos saem sem o dono; um agente nunca pode fazer isso (ARCHITECTURE, invariante 4).
- **`path` vem de fora:** canonicalizar com `toRealPath`, exigir diretório com repositório git, recusar caminho inexistente com mensagem sem eco do conteúdo.
- **`git` seguro:** `git -C <repo> show main:.shipfloor/factory.yml` sem shell, argumentos fixos, `GIT_TERMINAL_PROMPT=0`, `-c core.fsmonitor=false`, timeout, tamanho máximo do arquivo e saída padrão lida com limite. Nada do conteúdo do `factory.yml` vira comando.
- **YAML não confiável:** `snakeyaml-engine` em modo seguro (sem tipos arbitrários, limites de aliases e de tamanho); chaves que o registro não lê (as da V0: `board`, `language`, `limits`, `layers[].build` etc.) são ignoradas; os campos que lê são validados e o erro nomeia o campo.
- **Log:** só o id do projeto (nome validado) e o caminho; nunca o conteúdo do arquivo.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Teste novo `ProjectRegistryTest.kt`: nenhuma suíte do `daemon` cobre registro (só Module, Protocol, SocketServer); o AC-4 usa um repositório git temporário real, os demais usam um fake de `ProjectConfigSource`.
- `snakeyaml-engine` com versão fixa no catálogo: o projeto não tem parser YAML e o core não pode ter IO; o modo seguro evita instanciar tipos arbitrários.
- Ler via `git show main:...` e não do disco: é o que garante o AC-4 sem depender do branch em checkout.
- Branch lida é `main` fixa nesta fase: a `factory.yml` da V0 também é lida só da main; branch padrão configurável fica para quando houver necessidade.
- Registro em memória e CLI `project add/list` em card separado: já acordado no escopo do PO; persistência exigiria a camada `store` e tornaria o card `heavy`.
- Peso `standard`: uma camada, 6 critérios de aceitação (mais de 3).

## Open questions
- none

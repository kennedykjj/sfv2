# CLI: sfv2 project add e project list — spec

**Issue:** #206 · **Branch:** `feat/206-cli-sfv2-project-add-e-project-list` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o daemon tem o comando `project.add` (#20), mas a CLI não: para registrar o projeto na virada (#62) o dono monta o JSON à mão com a prova. Sem projeto registrado o dispatcher não age em nenhum card.
**Público:** o dono da fábrica.
**Critérios de aceitação**
- **AC-1** — Dado o daemon rodando, quando o dono roda `sfv2 project add <caminho>`, então o projeto é registrado (o daemon lê o `factory.yml` da `main`), o comando imprime o nome e o repositório, e a prova enviada vem do `owner.token`.
- **AC-2** — Dado um caminho que não é repositório válido ou um `factory.yml` inválido, quando roda, então falha com a mensagem do daemon (que nomeia o campo) e código de saída diferente de zero.
- **AC-3** — Dado um projeto já registrado, quando roda de novo com o mesmo caminho, então termina com código 0, sem duplicar e sem erro.
- **AC-4** — Dado `sfv2 project list`, quando roda, então lista os projetos registrados (nome e repositório), sem política nem texto de configuração.
**Fora de escopo:** remover projeto; alterar o daemon; editar o `factory.yml`.
**Fit do produto:** apoio à virada (S5-05), fase 5 (Autossuficiência).
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada única: `cli`. O protocolo do daemon não muda (`project.add` e `project.list` já existem).
- Contratos usados: `project.add {actor:"owner", path, proof}` → `result {name, repo, layers}`; `project.list {}` → `result [{name, repo, layers}]`. A CLI imprime só `name` e `repo` (uma linha `<name>  <repo>`), nunca `layers`.
- Arquivos: `cli/src/main/kotlin/sfv2/cli/ProjectCommands.kt` (novo: grupo `project` com `add` e `list`, e `withProjectCommands`), `cli/src/main/kotlin/sfv2/cli/Main.kt` (encadear `withProjectCommands(cli)`), `cli/src/test/kotlin/sfv2/cli/MainTest.kt` (testes novos).
- `project add <caminho>`: resolve o caminho com `toRealPath()` (o daemon registra o caminho canônico); se não resolve, envia o caminho como veio e deixa o daemon recusar. Antes de adicionar, chama `project.list`; se algum `repo` for igual ao caminho canônico, imprime a linha do projeto e sai com 0 sem chamar `project.add` (AC-3). Senão, lê o token (`cli.ownerToken()`), envia `project.add` e imprime a linha. Erro do daemon sai por `cli.fail(mensagem)` (código 1), já existente em `CliEnvironment.call`.
- Migração: n/a. Nenhum arquivo fora da camada `cli`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-2 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-3 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-4 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Idempotência (AC-3) na CLI, via `project.list` antes do `add`: o daemon responde erro para projeto repetido e mudá-lo sai da camada `cli` e do escopo da issue; casar texto de erro seria frágil.
- `project list` não envia token: é leitura, como `card list`.
- PO e Tech Lead escritos pelo Tech Lead: os critérios da issue já são Given/When/Then e a camada é única; sem UX (sem UI) nem Cybersec (sem label).
- Peso `standard` e não `light`: 4 critérios (limite de `light` é 3).
- Testes no `MainTest.kt` existente com o daemon falso já usado nos testes de `card`; nenhum arquivo de teste novo.

## Open questions
- none

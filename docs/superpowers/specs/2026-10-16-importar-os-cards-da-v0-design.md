# Importar os cards da V0 — spec

**Issue:** #60 · **Branch:** `feat/60-importar-os-cards-da-v0` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** A virada da V0 para a sfv2 não pode perder o estado do board atual: hoje o import cria todo card em Inbox, então o dono perderia em qual coluna cada trabalho estava.
**Audience:** O desenvolvedor sozinho com muitos projetos, migrando um projeto já operado pela V0.
**Acceptance criteria**
- **AC-1** — Dado o `board.tsv` da V0 (uma linha por número de issue, com o nome da coluna) e as issues abertas do repositório, quando o dono importa, então cada card nasce na coluna do mapeamento explícito abaixo (AC-5); issues abertas ausentes do board nascem em Inbox, e issues fechadas listadas como Done entram em Done.
- **AC-2** — Dado um card já importado, quando o dono importa de novo, então nenhum card é duplicado e a contagem de cards não muda.
- **AC-5** — Dado o nome de coluna da V0 `Technical Refinement` (a V0 chama de outro modo a coluna Refinement da sfv2), quando importa, então a linha vale como Ready for Refinement e não é reportada como coluna desconhecida; e uma linha em Ready for Dev, Coding, Validation, Staged ou Ready for Prod nasce em Spec Review, com `v0_column=<nome original>` no evento de criação, para o dono decidir o que reexecutar: a sfv2 não tem worktree, branch nem head aprovado desses cards, e um card plantado direto em Ready for Dev ou Validation dispararia um run sem estado (em Coding, Staged ou Ready for Prod ficaria parado ou voltaria ao dono com `no_approved_head`).
- **AC-3** — Dado uma linha do `board.tsv` com nome de coluna desconhecido, quando importa, então essa linha é reportada (número da issue e nome da coluna) e ignorada, e as demais linhas são importadas.
- **AC-4** — Dado uma linha do `board.tsv` cuja issue não existe no repositório, quando importa, então a linha é reportada com o número da issue e ignorada, e as demais são importadas.
**Out of scope:** Sincronização contínua com o GitHub; reimportar para mover um card já importado de coluna; migrar histórico, comentários ou custo da V0; novos formatos de board além do `board.tsv`.
**Product fit:** Serve "importar e fechar, sem sincronização contínua": o tracker é tocado só na importação. Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- Novo comando de protocolo `card.import_board` (dono, com `proof`): `{project?, rows: [{id, column}], includeOpen: true}`. Resposta: `CardsResult` mais lista de linhas ignoradas `{id, reason}` (`unknown_column` ou `not_found`), nunca texto de issue.
- Comando CLI `sfv2 card import-board <arquivo.tsv> [--project]`: lê o `board.tsv` (linhas `<issue>\t<coluna>`, linha vazia ignorada, colunas extras ignoradas), monta `rows` e imprime um card por linha e uma linha `ignorada: #<id> (<motivo>)` por descarte.

**Camadas e arquivos**
- `daemon`: `Protocol.kt` (request `CardImportBoard`, parse e resposta), `CardImport.kt` (`handle` ganha o caso; reaproveita `existing`, `create`, `refOf`), `Module.kt` (roteamento do request).
- `cli`: `CardCommands.kt` (`CardImportBoard`, registrado em `withCardCommands`).
- Para cada linha: coluna da V0 resolvida por tabela fixa: `Inbox`→Inbox, `Ready for Refinement`→Ready for Refinement, `Technical Refinement`→Ready for Refinement, `Spec Review`→Spec Review, `Ready for Dev`/`Coding`/`Validation`/`Staged`/`Ready for Prod`→Spec Review, `Done`→Done (nome exato; qualquer outro vira `unknown_column`); `tracker.fetch(id)` null → `not_found`; erro do tracker aborta o comando sem criar nada parcial além do já gravado (idempotente, rodar de novo completa). Card existente (referência github + id no projeto) é pulado sem evento. Card novo nasce direto na coluna (evento `CARD_CREATED` com `origin=v0`), sem passar por MoveRules.
- Após as linhas, `tracker.search` das issues abertas cria em Inbox as que não têm card nem linha no board (mesma regra do `card.import` por query).
- Sem migração de banco e sem contrato existente alterado.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Formato do `board.tsv` assumido como `<issue>\t<coluna>` (colunas extras ignoradas): o arquivo fica fora do repositório e do worktree; a V0 lista o board assim.
- Comando novo no daemon em vez de `card.import` + `card.move` na CLI: MoveRules não permite pular de Inbox direto para Done/Coding, e criar já na coluna é atômico.
- Peso heavy: toca duas camadas (daemon e cli), apesar da nota "cli/standard" da issue.
- Sem arquivo de teste novo: `CardImportTest.kt` já cobre o import; o teste de CLI do parse do tsv fica a critério do engenheiro em `cli/src/test/kotlin/sfv2/cli/MainTest.kt`.
- Card importado como Done com issue fechada é aceito (a regra de só abertas vale para a busca, não para as linhas do board).

- Colunas com trabalho em andamento na V0 (Ready for Dev a Ready for Prod) viram Spec Review, não a coluna "equivalente": a sfv2 só age sobre um card quando o estado dele existe nela (worktree, branch, head aprovado), e o dono reaprova o que quer reexecutar. `Technical Refinement` (nome da V0) vira Ready for Refinement. Opção mais conservadora; trocar o mapeamento é uma linha da tabela.
## Open questions
- none

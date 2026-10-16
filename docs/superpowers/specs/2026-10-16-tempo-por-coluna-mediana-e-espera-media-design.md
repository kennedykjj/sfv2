# Tempo por coluna: mediana e espera média — spec

**Issue:** #231 · **Branch:** `feat/231-tempo-por-coluna-mediana-e-espera-media` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** o quadro não diz quanto tempo os cards costumam ficar em cada coluna. O dono não vê onde o tempo vai nem quanto da espera é dele, que é a base de "Onde o tempo vai" e dos subtítulos de coluna do alvo do app (`DESIGN.md`, "Target: the Board").
**Audience:** o desenvolvedor sozinho com muitos projetos, que decide nos gates e quer saber o custo da própria espera.
**Acceptance criteria**
- **AC-1** — Dado cards que saíram de Refinement, Coding ou Validation dentro do período pedido, quando o tempo por coluna é calculado, então cada uma dessas colunas traz a mediana, em segundos, das permanências concluídas no período.
- **AC-2** — Dado cards que saíram de Spec Review ou Staged dentro do período pedido, quando o tempo por coluna é calculado, então cada uma dessas colunas traz a média, em segundos, das permanências concluídas, rotulada como espera.
- **AC-3** — Dado uma coluna sem nenhuma permanência concluída no período, ou uma coluna que não é de trabalho nem de gate (Inbox, filas, Ready for Prod, Done), quando o tempo por coluna é calculado, então ela não traz valor, nunca zero; uma permanência ainda aberta (o card segue na coluna) não entra na conta.
- **AC-4** — Dado um card que passa mais de uma vez pela mesma coluna (por exemplo Coding → Validation → Coding), quando o tempo é calculado, então cada visita concluída é uma permanência separada.
- **AC-5** — Dado um cliente do socket do daemon, quando envia `column.times` com `from` e `to`, então recebe, por coluna, o tipo da estatística (mediana ou espera média), o valor em segundos e o número de permanências; o pedido é de leitura, sem ator nem prova, e `from`/`to` ausentes ou inválidos são recusados.
- **AC-6** — Dado o quadro do app conectado ao daemon, quando uma coluna tem estatística, então o cabeçalho mostra o subtítulo "mediana 6 min" (trabalho) ou "espera média 41 min" (gate); sem estatística, nada é mostrado; o período é o dia local atual.
**Out of scope:** a barra "Onde o tempo vai" e a frase de percentual de espera (follow-up); tempo por card no cartão; filtro de período na tela; histórico por dia; ocultar o tempo de card que retornou de Done.
**Product fit:** serve "Onde o tempo vai" e a visibilidade da espera do dono (`DESIGN.md`, alvo do quadro); o tempo vem dos eventos, o daemon é a fonte única. Complementa o relatório por card da #61 sem repeti-lo: lá é por card em Done, aqui é por coluna.
**Touches UI:** yes

## UX
_UX/UI Designer_

O subtítulo entra no cabeçalho da coluna, abaixo do nome e da contagem, em `Texts` (pt-BR), fonte `sans` 13sp, cor `Tokens.muted`. Em coluna de gate o rótulo "espera média" usa a mesma cor, sem o acento: o acento marca só o que espera pelo dono agora. Valores: menos de 1 min = "< 1 min"; até 59 min = "N min"; a partir de 1 h = "N h MM min". O texto vai também na descrição de acessibilidade da coluna. Estado de carregamento ou falha da leitura: sem subtítulo, o quadro não muda.

## Architecture
_Tech Lead_

**Core (puro, sem IO nem relógio):** `core/src/main/kotlin/sfv2/core/ColumnTimes.kt` novo.
- `enum class ColumnTimeKind { MEDIAN, MEAN_WAIT }`; `data class ColumnTime(val kind: ColumnTimeKind, val seconds: Long, val stays: Int)`.
- `ColumnTimes.of(eventsByCard: List<List<Event>>, from: Instant, toExclusive: Instant): Map<Column, ColumnTime>`: por card, percorre `CARD_CREATED` e `CARD_MOVED` em ordem de `seq` (coluna inicial `INBOX`, como em `CardReport.stageSeconds`); cada `CARD_MOVED` fecha a permanência da coluna anterior (`at` do movimento menos o início). Entra na conta a permanência cujo fim está em `[from, toExclusive)`. Mediana (par: média dos dois centrais, truncada) para `REFINEMENT`, `CODING`, `VALIDATION`; média truncada para `SPEC_REVIEW`, `STAGED`; demais colunas ficam fora do mapa, e coluna sem permanência também.
- `daemon/CardReport.stageSeconds` não muda (soma por card, outro objetivo).

**Daemon:** `Protocol.kt`: `Request.ColumnTimes(from, to)` (`"cmd":"column.times"`, leitura), `Response.ColumnTimesResult(times: Map<Column, ColumnTime>)` serializado como `{"ok":true,"result":{"columns":[{"column":"CODING","kind":"MEDIAN","seconds":1320,"stays":7}]}}`, na ordem do quadro. `ColumnTimesQuery.kt` novo (`daemon/src/main/kotlin/sfv2/daemon/`): lê `store.cards()` e `store.events(id)` e delega a `ColumnTimes.of`. `SocketServer.kt` roteia o request; `CardCommands.kt` ganha o ramo no `when` ("not a card command"). Forma anterior do protocolo intacta (comando novo, aditivo).

**App:** `FeedSocket.kt` (ou arquivo vizinho do cliente do socket) ganha `SocketColumnTimesSource` com a chamada `column.times`; `BoardMonitor.kt` lê a cada passe do quadro e põe em `BoardView.columnTimes: Map<Column, ColumnTime>`; `BoardState.kt` ganha `ColumnTimeLabel.of(time): String` (formata a duração e o rótulo); `Theme.kt` (`Texts`) guarda os textos; `BoardScreen.kt` mostra o subtítulo no `Lane`. Falha ao ler o tempo mantém o quadro e esconde os subtítulos.

**Camadas e módulos:** `core`, `daemon`, `app`. Sem mudança em store, schema, config ou dependência. Sem migração.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/ColumnTimesTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/ColumnTimesTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/ColumnTimesTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/ColumnTimesTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: três camadas tocadas (core, daemon, app).
- Cálculo no `core`, não no daemon: é regra de negócio pura sobre eventos (critério de revisão 🟡); o daemon só lê o store e fala o protocolo.
- Arquivo de teste novo `ColumnTimesTest.kt`: nenhuma suíte do `core` cobre durações sobre eventos (`EventTest.kt` cobre o tipo do evento, não cálculo).
- O pedido do daemon é coberto em `ProtocolTest.kt` (parse e serialização, como `cost.summary`); o ramo de roteamento é uma linha no `when` existente.
- Mediana nas colunas de trabalho e média nas de gate, como no alvo (`docs/design/interface-alvo.html`): a espera do dono tem cauda longa, e a média mostra o custo total dela; o trabalho do agente é mais estável e a mediana resiste a outliers.
- Período `[from, toExclusive)` pelo fim da permanência, para o app pedir "hoje" (dia local) como o painel de custo faz; permanência aberta fica fora, pois ainda não tem duração final.
- Mediana de contagem par: média truncada dos dois centrais, em segundos inteiros (sem `Double`).
- Janela inicial do app é o dia local atual; filtro de período na tela fica fora do escopo.
- Sem valor (não zero) para coluna sem amostra, como a regra de custo desconhecido da #61.

## Open questions
- none

# Barra "Onde o tempo vai" e previsão na faixa — spec

**Issue:** #241 · **Branch:** `feat/241-barra-onde-o-tempo-vai-e-previsao-na-faixa` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** o quadro já mostra a mediana e a espera média no cabeçalho de cada coluna (#231) e a faixa já traz a previsão de fila vazia (#239), mas nada diz de relance quanto do tempo de um card é máquina e quanto é espera pelo dono. O alvo do app (`DESIGN.md`, "Where the time goes"; `docs/design/interface-alvo.html`) pede uma barra empilhada com o tempo por etapa e a frase "N% é espera por você", e a spec da #231 deixou essa barra como follow-up.
**Audience:** o desenvolvedor sozinho com muitos projetos, que decide nos gates e quer saber o custo da própria espera.
**Acceptance criteria**
- **AC-1** — Given o tempo por coluna do dia local com estatística em Refinement, Spec Review, Coding, Validation e Staged, when o app monta a barra, then ela tem um segmento por coluna com estatística, na ordem do fluxo, com largura proporcional aos segundos; colunas sem estatística não ganham segmento.
- **AC-2** — Given a barra montada, when há segmentos de espera (Spec Review, Staged) e de trabalho (Refinement, Coding, Validation), then a frase acima diz `mediana por etapa hoje · N% é espera por você`, onde N é o total dos segmentos de espera sobre o total de todos os segmentos, arredondado ao inteiro mais próximo (meio sobe), em aritmética inteira.
- **AC-3** — Given que nenhuma coluna tem estatística no dia, ou o total de segundos é zero, ou a leitura do tempo falhou, when o app monta a barra, then nenhuma barra nem frase aparece e o quadro não muda; nunca "0%" nem número inventado.
- **AC-4** — Given um segmento, when ele é desenhado, then a cor segue o estágio (refine, develop, validate) e os segmentos de espera usam o acento areia hachurado, com a duração em texto dentro do segmento quando cabe; a legenda lista etapa e duração, e a descrição de acessibilidade da barra lê todas as etapas com duração e a frase de percentual.
- **AC-5** — Given a aba Quadro aberta e conectada, when a janela é desenhada, then a barra aparece entre a faixa "A fábrica diz" e as colunas (só na visão Quadro), com a legenda oculta abaixo de 880 px, e fica esmaecida junto com o quadro quando o daemon não responde.
- **AC-6** — Given cards cujo título contém texto de instrução ou marcação, when a barra é montada, then ela cita só nomes de coluna e números, nunca título nem texto de card.
**Out of scope:** previsão na faixa (já entregue na #239; este card não a altera); tempo por card entregue (a barra usa a estatística por coluna que o daemon já informa, sem comando novo); filtro de período na tela; clique no segmento; histórico por dia; mudança de protocolo, core ou daemon.
**Product fit:** serve "Onde o tempo vai" e a visibilidade da espera do dono (`DESIGN.md`, alvo do quadro). Usa só o `column.times` existente, então o daemon segue como fonte única e o app só desenha.
**Touches UI:** yes

## UX
_UX/UI Designer_

Bloco fino entre a faixa e as colunas, recuo de 16 dp como a faixa. Título **Onde o tempo vai** em 13sp semibold; ao lado, a frase em `Tokens.muted` 13sp, com `N% é espera por você` em `Tokens.sand` semibold. Barra de 26 dp de altura, cantos de 8 dp, borda `Tokens.edge`. Segmentos de trabalho nas cores de estágio (`Tokens.refine`, `Tokens.develop`, `Tokens.validate`), texto da duração em Plex Mono 11sp sobre `Tokens.ground`; segmentos de espera em listras diagonais de `Tokens.sand`. Segmento estreito demais para o texto fica sem rótulo (a legenda e a descrição de acessibilidade o cobrem). Legenda abaixo: quadrado da cor, nome da etapa e duração, em `muted` 12sp; oculta abaixo de 880 px. A informação não depende só da cor: a espera é distinta por hachura e pela frase. Estado esmaecido igual ao do quadro desatualizado. Textos em `Texts` (pt-BR). Verificação visual com captura em desktop (≥1100 px) e estreito (<880 px).

## Architecture
_Tech Lead_

**App (única camada):**
- `app/src/main/kotlin/sfv2/app/BoardState.kt`: `data class TimeSegment(val column: Column, val seconds: Long)` e `data class TimeSplit(val segments: List<TimeSegment>, val waitPercent: Int)` com `TimeSplit.of(times: Map<Column, ColumnTime>): TimeSplit?`. Puro: segmentos na ordem Refinement, Spec Review, Coding, Validation, Staged, só com estatística; `waitPercent` = `(2 * espera + total) / (2 * total)` em `Long`; `null` quando não há segmento ou o total é zero. A espera usa `isWaitingForOwner`.
- `app/src/main/kotlin/sfv2/app/FlowBar.kt` novo: composable `FlowBar(split, dimmed, modifier)`, barra, frase e legenda; cor por `stageHue`.
- `app/src/main/kotlin/sfv2/app/Main.kt`: encaixa `FlowBar` entre `PulseStrip` e `BoardScreen`, alimentada por `view.columnTimes` (já lido pelo `BoardMonitor` a cada snapshot e movimento) e esmaecida quando `!view.live`.
- `app/src/main/kotlin/sfv2/app/Theme.kt` (`Texts`): título, frase com percentual, descrição de acessibilidade.

**Camadas e módulos:** só `app`. Sem mudança em core, daemon, store, protocolo, schema, config ou dependência.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | manual | `app/src/main/kotlin/sfv2/app/FlowBar.kt` |
| AC-5 | manual | `app/src/main/kotlin/sfv2/app/FlowBar.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma só camada (`app`), mas `Touches UI` é `yes`.
- A previsão da faixa já existe (#239, `PulseStrip.kt`); o "previsão na faixa" do título fica coberto por ela e sem alteração aqui.
- A barra usa o `column.times` existente (mediana nas colunas de trabalho, média nas de espera) em vez de um comando novo "por card entregue": evita tocar core e daemon e o protocolo; o rótulo da frase diz "mediana por etapa", não "por card entregue", para não afirmar o que não mede.
- Mistura de mediana e média no total é aceita: a frase vira uma razão de linha de base, coerente com os subtítulos de coluna da #231; trocar por tempo por card é follow-up.
- Regra do percentual no `app` (`BoardState.kt`) e não no `core`: é apresentação sobre dados já calculados no `core`; sem relógio nem IO, e fica testável como `ColumnTimeLabel`.
- Sem arquivo de teste novo: `BoardStateTest.kt` é a suíte do modelo do quadro e já cobre `ColumnTimeLabel`.
- AC-4 e AC-5 são `manual`: desenho e layout responsivo em Compose não têm suíte automatizada; verificação por captura em desktop e estreito.
- Percentual em inteiro com meio para cima, sem `Double`.
- Leitura de tempo falha: o `BoardMonitor` já devolve mapa vazio, então a barra some em vez de mostrar dado velho.

## Open questions
- none

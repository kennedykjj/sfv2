# Previsão de término da fila — spec

**Issue:** #239 · **Branch:** `feat/239-previsao-de-termino-da-fila` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** a faixa "A fábrica diz" mostra estado, pool e teto do dia, mas não diz quando a fila esvazia. O dono não sabe se vale esperar a fábrica terminar ou se o que ele segura (Spec Review, Staged) é o que atrasa. O alvo do app (`docs/design/interface-alvo.html`, célula "Previsão": "Fila vazia ~16:15") pede esse número, e a spec da #235 deixou a célula para este card.
**Audience:** o desenvolvedor sozinho com muitos projetos, que decide nos gates e quer saber quando a fábrica fica ociosa.
**Acceptance criteria**
- **AC-1** — Given cards abertos em colunas de fila ou de trabalho e as medianas de Refinement, Coding e Validation, when a previsão é calculada, then cada card soma a mediana das colunas de trabalho que ainda faltam até Staged (do card em Ready for Refinement ou Refinement: as três; em Ready for Dev ou Coding: Coding e Validation; em Validation: só Validation), o total é dividido pela capacidade do pool com arredondamento para cima, e o término é o instante atual mais esse tempo.
- **AC-2** — Given cards em Spec Review ou Staged, when a previsão é calculada, then eles não somam tempo de espera (a previsão é de linha de base, sem a demora do dono) e a previsão informa quantos cards o dono segura; cards em Inbox, Ready for Prod e Done não entram na conta.
- **AC-3** — Given que nenhum card precisa de trabalho de máquina, when a previsão é calculada, then o resultado é "fila vazia", sem horário; given capacidade do pool zero ou negativa, ou uma coluna de trabalho necessária sem mediana no período, then o resultado é "sem previsão" com o motivo (sem pool ou sem histórico), nunca um horário inventado nem zero.
- **AC-4** — Given um cliente do socket, when envia `factory.pulse`, then a resposta traz, além dos campos atuais, a previsão calculada com as medianas dos últimos 7 dias; o pedido segue sendo leitura, sem evento, e a previsão cita só números e instantes, nunca título.
- **AC-5** — Given a leitura de pulso com previsão e o modelo da faixa, when o app monta a célula, then mostra "Fila vazia ~HH:mm" (hora local; "amanhã HH:mm" quando passa da meia-noite), "Fila vazia" quando não há trabalho, ou "sem previsão" com o motivo; mostra a linha "O dono segura N passos" quando N > 0; com leitura desatualizada, mantém a última previsão esmaecida.
- **AC-6** — Given a aba Quadro conectada, when a janela é desenhada, then a faixa mostra a quarta célula "Previsão" (rótulo "baseline"), em 4 células acima de 1100 px, 2 por 2 abaixo disso e 1 coluna abaixo de 880 px.
**Out of scope:** caminho crítico por dependência ("Caminho longo: #62 → #78 → #79"): o modelo não guarda dependências entre cards e isso é card próprio; demora do dono no horário (a previsão é de linha de base); crédito pelo tempo já gasto num card em andamento; janela de período configurável; previsão por projeto.
**Product fit:** serve "A fábrica diz" e a visibilidade da espera do dono (`DESIGN.md`, alvo do quadro); o número vem dos eventos, o daemon é a fonte única e o app só desenha. Reusa o tempo por coluna da #231.
**Touches UI:** yes

## UX
_UX/UI Designer_

Quarta célula da faixa, título **Previsão** com o rótulo `baseline` ao lado, no padrão das outras (`DESIGN.md`, "Target: the Board"). Linha principal em 15sp (`Fila vazia ~16:15`), linha secundária em `Tokens.muted` 13sp (`O dono segura 2 passos`; singular "1 passo"). Sem previsão: "sem previsão" em `muted`, com o motivo ("sem sessões no pool" ou "sem histórico de tempo"). Nada de cor nova; o acento areia não é usado, pois a célula informa e não pede ação. Desatualizada: esmaecida como o resto da faixa. Textos em `Texts` (pt-BR); `contentDescription` lê a frase completa. Verificação visual com captura em desktop (≥1100 px) e estreito (<880 px).

## Architecture
_Tech Lead_

**Core (puro, sem IO nem relógio):** `core/src/main/kotlin/sfv2/core/QueueForecast.kt` novo.
- `sealed interface QueueForecast { data object Empty; data class FinishesAt(val at: Instant, val ownerSteps: Int); data class Unknown(val gap: ForecastGap) }` e `enum class ForecastGap { NO_POOL, NO_HISTORY }`.
- `QueueForecasts.of(columns: List<Column>, medians: Map<Column, ColumnTime>, poolCapacity: Int, now: Instant): QueueForecast`: por card, soma a mediana (`ColumnTimeKind.MEDIAN`) das colunas de trabalho restantes; `ownerSteps` conta `SPEC_REVIEW` e `STAGED` (`OWNER_WAIT_COLUMNS`); término = `now` + `ceil(total / capacidade)` em `Long`, sem `Double`.

**Daemon:** `FactoryPulse.kt` calcula a previsão a cada `factory.pulse`: colunas dos cards abertos (sem o card de configurações), `ColumnTimes.of(eventos, now − 7 dias, now + 1 s)` e `poolCapacity()`; delega a `QueueForecasts.of`. `Protocol.kt`: `Response.PulseResult` ganha `forecast`, serializado como `"forecast":{"kind":"EMPTY"}`, `{"kind":"FINISHES_AT","at":"2026-10-16T19:15:00Z","ownerSteps":2}` ou `{"kind":"UNKNOWN","gap":"NO_HISTORY"}`. Campo aditivo: a forma anterior do protocolo segue válida.

**App:** `PulseSource.kt` lê `forecast` (ausente vira `null`, daemon antigo); `PulseModel.kt` ganha `forecast` em `Pulse` e `PulseView` e o texto em `PulseTexts`; `PulseStrip.kt` desenha a quarta célula e ajusta a grade; `Theme.kt` (`Texts`) guarda os textos.

**Camadas e módulos:** `core`, `daemon`, `app`. Sem mudança em store, schema, config ou dependência. Sem migração.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/QueueForecastTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/QueueForecastTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/QueueForecastTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | manual | `app/src/main/kotlin/sfv2/app/PulseStrip.kt` |

## Security
_Cybersec_ — n/a

## Rollback
Reverter o PR. O campo `forecast` é aditivo: app antigo ignora; app novo com daemon antigo recebe a resposta sem o campo e mostra "sem previsão". Nenhuma migração nem dado persistido.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: três camadas tocadas (core, daemon, app) e `## Rollback` com conteúdo.
- Cálculo no `core`, não no daemon: é regra de negócio pura sobre colunas e medianas (critério de revisão 🟡); o daemon só junta os dados.
- Arquivo de teste novo `QueueForecastTest.kt`: nenhuma suíte do `core` cobre previsão (`ColumnTimesTest.kt` cobre permanências por coluna, outro objeto).
- AC-4 em `SocketServerTest.kt`: é onde `factory.pulse` já é testado de ponta a ponta; AC-5 em `BoardStateTest.kt`, suíte do modelo da faixa no módulo `app`.
- AC-6 é `manual`: layout Compose responsivo não tem suíte automatizada; verificação por captura em desktop e estreito.
- Previsão de linha de base, sem a espera do dono: o dono só aparece como "segura N passos"; somar a espera dele misturaria uma decisão humana num número de máquina.
- Sem caminho crítico: o modelo não guarda dependências entre cards; fica para card próprio.
- Mediana (não média) das últimas 7 dias: o dia local pode ainda não ter permanências no começo da manhã; a mediana resiste a outliers. Falta de mediana vira "sem previsão", nunca zero.
- Card em andamento conta a mediana inteira da coluna atual (sem crédito pelo tempo gasto): estimativa conservadora e simples; refinar é follow-up.
- Divisão por capacidade do pool, arredondada para cima em segundos inteiros: assume trabalho perfeitamente paralelizável, coerente com o rótulo `baseline`.
- Campo `forecast` ausente no app é `null` e vira "sem previsão": compatível com daemon antigo.

## Open questions
- none

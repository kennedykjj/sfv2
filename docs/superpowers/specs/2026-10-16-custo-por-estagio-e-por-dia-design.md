# Custo por estágio e por dia — spec

**Issue:** #232 · **Branch:** `feat/232-custo-por-estagio-e-por-dia` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O painel de custo só quebra o gasto por card. O dono não vê em que estágio (refine, develop, validate, conflito) o dinheiro vai, nem como o gasto evolui de um dia para o outro, e por isso não sabe onde cortar custo nem se o teto diário está bem calibrado.
**Audience:** O desenvolvedor sozinho com muitos projetos, que paga a fábrica do próprio bolso e precisa ver para onde o gasto vai.
**Acceptance criteria**
- **AC-1** — Dado eventos `RUN_COST_RECORDED` de estágios diferentes no período, quando peço `cost.summary`, então o resultado traz `stages` com um total por estágio (campo `stage` do evento), cada um com valor e status, ordenados por nome; evento sem `stage` conta no estágio `unknown`.
- **AC-2** — Dado eventos de custo em mais de um dia local no período, quando peço `cost.summary` informando `zone`, então o resultado traz `days` com um total por dia local (`AAAA-MM-DD`) em ordem cronológica, e o dia de um evento é decidido pelo `zone` pedido, não pelo UTC.
- **AC-3** — Dado `cost.summary` sem `zone`, quando o daemon o processa, então os dias são calculados em UTC; um `zone` que não é um identificador de fuso válido é rejeitado com erro que diz o que aconteceu.
- **AC-4** — Dado qualquer período, quando somo por estágio e por dia, então a soma e o status usam a mesma conta do `CostBudget` (`CostSums`): estágio ou dia com run UNKNOWN fica UNKNOWN ou PARTIAL como no total, nunca um número exato, e a soma dos estágios (e dos dias) é igual ao `totalUsd`.
- **AC-5** — Dado o painel de custo do app, quando o daemon devolve `stages` e `days`, então o painel mostra "Por estágio" e, quando o período cobre mais de um dia, "Por dia", cada linha com valor formatado, tag `parcial`/`desconhecido` e o rótulo de estimativa, sem recalcular nada no app.
- **AC-6** — Dado um daemon que não devolve `stages` nem `days` (versão anterior), quando o app lê o `cost.summary`, então o painel aparece como hoje, sem as seções novas e sem erro; e o formato anterior do resultado (`totalUsd`, `status`, `cards`, `limits`, `dayReached`, `cardsReached`) não muda.
**Out of scope:** Limite de custo por estágio ou por dia da semana; gráfico; exportação de CSV por estágio (o `report.csv` não muda); histórico além do período pedido; custo em outra moeda; mudar como o custo de uma run é calculado ou registrado.
**Product fit:** Serve "custo medido das transcrições de cada run" e "custo é estimativa, sempre rotulada". É leitura pura sobre eventos já gravados: não cria estado nem efeito fora da máquina.
**Touches UI:** yes

## UX
_UX/UI Designer_

- **Onde:** no `CostPanel` existente, abaixo de "Por card", dois blocos novos com o mesmo desenho das linhas de card (rótulo à esquerda, valor tabular à direita): "Por estágio" e "Por dia". Sem tela, navegação ou componente novo.
- **Estágio:** rótulo é o nome do estágio como o daemon o devolve (`refine`, `develop`, `validate`, `conflict`), `soft`; ordenado do maior para o menor valor, como "Por card". Sem barra e sem limite (não existe limite por estágio).
- **Dia:** rótulo `dd/MM`, em ordem cronológica, mais recente por último; o bloco só aparece quando o período tem mais de um dia (o painel atual mostra um dia, então hoje o bloco fica oculto e não gera linha redundante com o total).
- **Parcial/desconhecido:** igual ao painel atual: `≥ US$ …` com tag `parcial`, `US$ ?` com tag `desconhecido`; a explicação única do painel passa a cobrir também estas linhas.
- **Vazio e antigo:** sem eventos ou sem os campos novos, os blocos não aparecem; nada de erro ou texto extra.
- **Acessibilidade e tokens:** textos 13-14px, tabular nos valores, tags com descrição de conteúdo, só tokens do `DESIGN.md`. Textos novos em `Texts`: "Por estágio", "Por dia".

## Architecture
_Tech Lead_

**Contrato (`cost.summary`, aditivo, forma anterior mantida)**
- Pedido: `{"cmd":"cost.summary","from":…,"to":…,"zone":"<id IANA, opcional>"}`; `zone` ausente = UTC; inválido → `Request.Rejected("invalid zone")`.
- Resultado ganha `"stages":[{"stage","costUsd","status"}]` e `"days":[{"day":"AAAA-MM-DD","costUsd","status"}]`; os campos atuais ficam como estão.
- O fuso vem no pedido (não do `Main`): as configurações de fuso mudam em execução e o app já conhece o dele, então o daemon continua sem ler relógio nem configuração nova.

**Camadas e módulos**
- `daemon`: `Protocol.kt` (`Request.CostSummary` com `zone: ZoneId`, `StageCost`, `DayCost`, campos novos em `CostSummaryResult`, parse e render), `CostSummaryQuery.kt` (agrupa os mesmos eventos de `costEventsBetween` por `payload["stage"]` e por `at.atZone(zone).toLocalDate()`, somando com `CostSums.sum` e `statusOf`).
- `app`: `FeedSocket.kt` (lê `stages`/`days` com tolerância a ausência e envia o `zone` do app), `FeedModel.kt` (`CostSummary` e `CostSummaryView` ganham as listas; só formata e ordena), `CostPanel.kt` e `Theme.kt` (blocos e textos).
- Nenhuma consulta nova no store: o intervalo já vem de `costEventsBetween`. Sem esquema novo, sem migração.

**Falhas:** estágio ausente → `unknown`; evento com `cost_usd` ilegível segue a regra existente de `CostSums`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CostSummaryQueryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CostSummaryQueryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CostSummaryQueryTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- O texto da issue não estava disponível para o refine (só o título): o escopo foi inferido do título e do código de custo existente; os ACs são a interpretação mínima e aditiva.
- Peso `heavy`: duas camadas (`daemon` e `app`).
- Quebra por estágio e por dia no `cost.summary` existente, não num comando novo: reaproveita a consulta indexada e a conta única de `CostSums`/`CostBudget`; campos aditivos mantêm o contrato.
- `zone` no pedido, com UTC por padrão: evita ligar configuração no `Main` e o app já conhece o fuso do usuário.
- Sem arquivo de teste novo: `CostSummaryQueryTest.kt`, `ProtocolTest.kt` e `FeedTest.kt` já cobrem os módulos.
- `report.csv` fica de fora: é outro contrato (CSV de cards Done) e não foi pedido.

## Open questions
- none

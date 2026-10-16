# Painel do card completo: gates, spec com testes, PR, bloqueios e custo por estágio — spec

**Issue:** #242 · **Branch:** `feat/242-painel-do-card-completo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** O painel lateral já existe (#238), mas o conteúdo ainda é o da tela cheia antiga: gates e critérios soltos na aba Summary, spec sem relação com os testes, nenhuma menção ao PR, ao motivo do bloqueio nem ao custo por estágio (o dono só vê a lista bruta de runs). Para decidir sem sair do quadro, o dono precisa ver num relance em que pé o card está: o que passou, o que falhou, qual PR, por que parou e quanto cada estágio custou.
**Audience:** o dono da fábrica, que consulta e decide cards olhando o quadro.
**Acceptance criteria**
- **AC-1** — Given um card com gates executados, when o painel exibe a aba Summary, then lista cada gate com veredito (PASS ou FAIL em texto, não só cor), duração e, quando FAIL, o motivo; sem gates executados mostra o estado vazio já existente.
- **AC-2** — Given um card com spec válida e critérios, when o painel exibe a aba Spec, then cada critério AC-n aparece junto do texto da spec com a camada, o arquivo de teste e o resultado do gate daquela camada (`PASS`, `FAIL`, `none` ou `manual`, com o nome do estado em texto); critério `manual` não finge resultado automático.
- **AC-3** — Given um card que já teve PR (evento com campo `pr` em `CARD_MOVED`, `OWNER_APPROVED` ou `PR_MERGED`), when o painel exibe a aba Summary, then mostra "PR #n" com o estado: aberto (aguardando decisão), mergeado (`PR_MERGED`) ou, sem PR, nada; o número vem do último evento que o traz.
- **AC-4** — Given um card com `CARD_BLOCKED` sem `CARD_UNBLOCKED` posterior, when o painel exibe a aba Summary, then mostra "bloqueado" com o motivo do último bloqueio e o instante; após `CARD_UNBLOCKED` o aviso some e o bloqueio anterior fica só nos eventos; sem motivo no payload mostra "motivo não informado".
- **AC-5** — Given um card com runs registrados, when o painel exibe a aba Runs, then agrupa o custo por estágio (nome verbatim do estágio, na ordem em que apareceram), mostra o subtotal de cada um em `US$ x,xx est.` e o total do card; estágio cujo custo é desconhecido mostra `US$ ?`, nunca zero, e o total vira parcial quando falta algum.
- **AC-6** — Given cards cujo motivo de bloqueio, texto de spec, nome de gate ou motivo de falha contêm instrução ou marcação, when o painel os exibe, then tudo é desenhado como texto simples, sem interpretar marcação e sem acionar nada.
**Out of scope:** link clicável para o PR e para a issue (`card.detail` não traz as referências do card; exige mudança no daemon, outro card); estado ao vivo do PR no GitHub (CI, revisões); gráfico de custo; mudança no daemon, no protocolo do socket ou em ferramenta MCP; novos comandos do dono.
**Product fit:** o dono consulta e decide sem sair do quadro, só apresentação do que a fábrica já registrou, sem aumentar o que o app escreve (PRODUCT.md).
**Touches UI:** yes

## UX
_UX/UI Designer_

Segue `DESIGN.md` (painel lateral, tokens existentes); nenhum token novo.
- Summary, de cima para baixo: bloqueio (se houver, em `bad` com texto "bloqueado" e o motivo), PR, gates. O bloqueio vem primeiro porque é o que exige ação.
- Gates: uma linha por gate com o veredito em texto (PASS, FAIL) e duração em figuras tabulares; o motivo da falha abaixo, em `soft`, quebrando linha.
- Spec: critérios em bloco acima do texto da spec, cada linha `AC-n · camada · arquivo · resultado`; arquivo em mono.
- Runs: cabeçalho por estágio com subtotal à direita (tabular), runs do estágio recuados abaixo; total do card no fim, em negrito.
- Custo sempre rotulado como estimativa (`est.`), em `text`, conforme `DESIGN.md`.
- Acessibilidade: resultado nunca só por cor; blocos como seções com título; o painel mantém o foco e a região já definidos em #238.
- Movimento: nenhum novo.
- Verificação visual com captura em janela larga e estreita, com um card bloqueado com PR e um card com custo parcial.

## Architecture
_Tech Lead_

- `app/src/main/kotlin/sfv2/app/CardDetailModel.kt`: funções puras sobre o `CardDetail` já recebido, sem mudar o parse nem o daemon: `cardPullRequest(events)` (número e estado do PR), `openBlock(events)` (motivo e instante do bloqueio em aberto ou nulo), `costByStage(runs)` (subtotal por estágio na ordem de aparição, com `CostStatus` por estágio) e `criterionResult(criterion)` (rótulo textual do resultado).
- `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt`: `PanelBody` compõe Summary (bloqueio, PR, gates), Spec (critérios + texto) e Runs (por estágio + total) com as funções acima; texto sempre via `BasicText`.
- `app/src/main/kotlin/sfv2/app/Theme.kt`: textos pt-BR em `Texts` (bloqueado, motivo não informado, PR aberto/mergeado, rótulos de resultado, subtotal).
- Sem mudança no daemon, no protocolo do socket nem em ferramenta MCP: tudo sai de `card.detail` (eventos, runs, gates, critérios).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-6 | manual | `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`app`), 3 caminhos, mas `Touches UI` é `yes`; sem daemon e sem segurança nova.
- O texto da issue não veio pelas ferramentas (só o título); o escopo vem do título e do painel entregue em #238 e #282.
- PR, bloqueio e custo por estágio saem dos eventos e runs que `card.detail` já devolve: levar as referências do card ao painel muda o daemon (duas camadas, peso heavy) e fica como card próprio, junto do link clicável.
- Regras em funções puras no `CardDetailModel.kt`, testadas em `CardDetailModelTest.kt`: é a suíte do modelo do painel e já cobre `Loaded`, gates e critérios; arquivo novo só repetiria a configuração.
- O gate de GATE_FINISHED e os critérios já vêm do daemon (`gates`, `criteria`); AC-1 e AC-2 testam a apresentação (rótulo textual, ordem), não o cálculo do daemon.
- AC-6 é `manual`: renderização Compose não tem suíte automatizada; o texto já é desenhado com `BasicText`, que não interpreta marcação.
- Estado do PR é inferido só dos eventos (aberto até `PR_MERGED`); não consulta o GitHub porque o app não fala com o forge.

## Open questions
- none

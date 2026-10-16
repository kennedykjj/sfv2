# Painel lateral do card — spec

**Issue:** #238 · **Branch:** `feat/238-painel-lateral-do-card` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** Hoje abrir um card troca o quadro inteiro pela tela de detalhe (`CardDetailScreen`), e o cartão do quadro nem reage a clique: só a paleta (Ctrl K) e a notificação abrem o card. O dono perde o quadro de vista a cada consulta. O alvo decidido em `DESIGN.md` ("The work area becomes a side panel") pede um painel lateral que abre num clique no cartão, sem sair do quadro.
**Audience:** o dono da fábrica, que consulta e decide cards olhando o quadro.
**Acceptance criteria**
- **AC-1** — Given o quadro com cards, when o dono clica num cartão (ou o foca e aperta Enter ou Espaço), then o painel abre com aquele card, o quadro continua visível e as colunas não mudam; clicar noutro cartão troca o card do painel sem fechá-lo; abrir pela paleta ou pela notificação do desktop usa o mesmo painel em vez da tela cheia.
- **AC-2** — Given o painel aberto, when ele é exibido, then oferece as abas Summary, Spec, Events e Runs, abre sempre em Summary, e a escolha de aba é mantida ao trocar de card; no topo mostra o caminho do card pelas dez colunas, em ordem do quadro, marcando as já percorridas, a atual e as seguintes, com o nome verbatim da coluna e sem depender só de cor.
- **AC-3** — Given o painel aberto, when o dono o redimensiona pela borda esquerda (arrasto ou setas Esquerda e Direita com o foco na borda), then a largura fica entre 360 e 680 px, nunca fora disso, e é mantida ao fechar e reabrir na mesma sessão.
- **AC-4** — Given a largura da janela, when ela é menor que 880 px, then o painel sobrepõe o quadro em vez de dividir a linha com ele; com 880 px ou mais, o painel divide a linha e o quadro encolhe sem perder a rolagem lateral; na largura mínima da janela o painel ocupa a largura disponível, sem rolar para o lado.
- **AC-5** — Given o painel aberto, when o dono aperta Esc fora de um campo de texto, then o painel fecha e o foco volta ao cartão que o abriu; Esc dentro do campo de nota de "Request change" cancela só a nota, como já faz.
- **AC-6** — Given um card em coluna que espera o dono (Spec Review ou Staged), when o painel está aberto, then o rodapé fixo mostra as ações de decisão que o app já oferece para aquela coluna, com o destino da aprovação; em qualquer outra coluna o rodapé não mostra ação de decisão e o painel não oferece nenhum comando novo ao daemon.
- **AC-7** — Given um card que o daemon não encontra (removido ou id inválido) ou o daemon fora do ar, when o painel está aberto, then ele mostra o estado "não encontrado" ou "desconectado" com instrução de saída, e o quadro ao lado continua utilizável.
- **AC-8** — Given cards cujo título, spec ou eventos contêm texto de instrução ou marcação, when o painel os exibe, then tudo é desenhado como texto simples, sem interpretar marcação e sem acionar nada.
**Out of scope:** link para a issue no rodapé dos cards que não esperam o dono (`card.detail` não traz as referências do card; exige mudança no daemon, outro card); barra de passos e dial do card em execução; "n de N decisões"; minimapa, colunas redimensionáveis e densidade em chips (outros cards); qualquer comando novo do daemon.
**Product fit:** consultar e decidir sem sair do quadro, só apresentação, sem aumentar o que o app escreve (PRODUCT.md).
**Touches UI:** yes

## UX
_UX/UI Designer_

Segue `DESIGN.md` ("Target: the Board", "drawer" nos tokens e "Elevation & Depth"); nenhum token novo.
- Painel: superfície `panel`, borda esquerda `edge-hi`, sombra "Drawer cast" só enquanto aberto; altura total abaixo do cabeçalho; largura inicial 480 px, limites 360 a 680.
- Topo: número do card (mono, na cor do estágio), título (texto simples), custo `est.` e o caminho pelas dez colunas como trilho de dez marcas, com a coluna atual destacada e o nome dela em texto.
- Abas em linha, a ativa com sublinhado e peso, não só cor; rodapé fixo com as ações de decisão (acento só nelas).
- Borda de redimensionar: alvo de 8 px, focável, com o foco visível (outline `sand` de 2 px).
- Acessibilidade: o painel é uma região com nome "Card #n"; ao abrir o foco vai ao título do painel, ao fechar volta ao cartão; abas com papel de aba e estado selecionado; a posição do card no caminho vai na descrição ("etapa 3 de 10: Ready for Dev").
- Movimento: sem animação nova; a abertura é imediata (nada a reduzir).
- Verificação visual com captura em janela larga (compartilhando a linha) e estreita (sobreposta).

## Architecture
_Tech Lead_

- `app/src/main/kotlin/sfv2/app/BoardState.kt`: modelo puro do painel, sem Compose: `SidePanelState` (card aberto, aba, largura) com `open`, `close`, `selectTab` e `resize` com limites 360 a 680; `PanelTab` (Summary, Spec, Events, Runs); `panelPlacement(windowWidth)` que devolve lado a lado ou sobreposto (limiar 880); `cardPath(column)` com as dez colunas e o estado de cada uma (percorrida, atual, seguinte); `panelFooter(column)` que diz se o rodapé mostra decisão (colunas de `OWNER_DECISION_COLUMNS`) ou nada.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: cartão clicável e ativável por teclado; destaque do cartão aberto; callback `onOpenCard`.
- `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt`: o conteúdo atual vira o corpo do painel, repartido nas quatro abas, reaproveitando as seções existentes (`SpecReviewActions`, `ClosureEntry`, gates, critérios, eventos, runs); a tela cheia deixa de ser usada pelo quadro.
- `app/src/main/kotlin/sfv2/app/Main.kt`: `openCardId` alimenta o painel ao lado do quadro em vez de `OpenCard` em tela cheia; paleta e `WindowRequest.Open` usam o mesmo caminho; Esc fecha o painel quando a paleta está fechada e o foco não está num campo de texto.
- `app/src/main/kotlin/sfv2/app/Theme.kt`: textos pt-BR do painel em `Texts` (abas, caminho, estados).
- Sem mudança no daemon, no protocolo do socket nem em ferramenta MCP: o painel usa `card.detail` e os comandos do dono que o app já tem.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-8 | manual | `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`app`), mas `Touches UI` é `yes`; sem daemon, sem segurança nova e menos de 8 caminhos.
- O texto da issue não veio pelas ferramentas (só o título); o escopo vem do título e da seção "Target: the Board" do `DESIGN.md` ("The work area becomes a side panel").
- Rodapé de "link da issue" fica fora: `card.detail` não devolve as referências do card, e incluí-las muda o daemon (duas camadas, peso heavy); vira card próprio.
- Abas e rodapé reaproveitam as seções e ações de hoje em vez de reescrevê-las: o painel é nova moldura, não nova função.
- Paleta e notificação passam a abrir o painel: manter a tela cheia em paralelo deixaria dois caminhos para o mesmo conteúdo.
- Estado e regras do painel em funções puras no `BoardState.kt`, testadas em `BoardStateTest.kt`: é a suíte do modelo do quadro e não há teste de Compose no módulo; arquivo novo só repetiria a configuração.
- AC-7 testa em `CardDetailModelTest.kt`, que já cobre `NotFound` e `Disconnected`; a exibição dentro do painel é conferida na captura.
- AC-8 é `manual`: renderização Compose não tem suíte automatizada; o texto já é desenhado com `BasicText`, que não interpreta marcação.
- A largura do painel é lembrada só na sessão (memória do app); persistir exigiria configuração nova.

## Open questions
- none

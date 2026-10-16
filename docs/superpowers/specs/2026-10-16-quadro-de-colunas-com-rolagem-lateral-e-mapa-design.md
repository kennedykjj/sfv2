# Quadro de colunas com rolagem lateral e mapa — spec

**Issue:** #230 · **Branch:** `feat/230-quadro-de-colunas-com-rolagem-lateral-e-mapa` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** a esteira atual rola na horizontal sem dar noção de onde a janela está entre as 10 colunas: não há mapa, o cabeçalho de coluna sai da tela com a rolagem vertical e a coluna só é tão alta quanto o conteúdo. O alvo decidido em `DESIGN.md` ("Target: the Board") pede o Quadro: colunas de altura total, cabeçalho fixo no topo, mapa de colunas acima e bordas que indicam que há mais para ver.
**Audience:** o desenvolvedor sozinho com muitos projetos, que precisa achar de relance a coluna onde a fábrica parou para ele, mesmo numa janela estreita.
**Acceptance criteria**
- **AC-1** — Given o quadro com as 10 colunas, when o mapa é calculado, then ele tem uma caixa por coluna, na ordem do quadro, com a contagem de cards, e a largura de cada caixa é proporcional à largura renderizada da coluna.
- **AC-2** — Given uma janela mais estreita que o quadro e uma posição de rolagem, when o mapa é calculado, then o contorno da janela visível começa e termina na proporção correspondente do mapa; com a janela do tamanho do quadro ou maior, o contorno cobre o mapa inteiro.
- **AC-3** — Given um clique numa caixa do mapa, when o destino de rolagem é calculado, then a coluna fica alinhada à borda esquerda da janela, limitado ao fim do quadro (nunca rola além do último pixel, nunca abaixo de zero).
- **AC-4** — Given o quadro sem snapshot ainda ou sem cards, when o mapa é calculado, then as 10 caixas existem com contagem zero, sem divisão por zero e sem erro; com largura de janela zero o contorno é vazio.
- **AC-5** — Given a posição de rolagem lateral, when o quadro é exibido, then a borda esquerda esmaece só se há o que ver à esquerda e a direita só se há o que ver à direita; sem rolagem possível nenhuma borda esmaece.
- **AC-6** — Given o quadro exibido, when ele rola na vertical, then o cabeçalho de cada coluna (nome e contagem) fica fixo no topo, e toda coluna se estica até a altura total da área de rolagem, mesmo vazia.
**Out of scope:** redimensionar colunas, "ajustar à janela", equalizar e colunas vazias recolhidas em faixa de 46 px; densidade Chips; rodapé de custo da coluna; faixa de pulso, "onde o tempo vai" e paleta de comandos; painel lateral do card; cor por estágio na borda superior; mapa por teclado além do foco normal (cada caixa é focável e responde a Enter/Espaço).
**Product fit:** serve à leitura de relance do estado local pelo dono (PRODUCT.md) sem aumentar o que o app escreve: é só apresentação, sem comando novo no daemon.
**Touches UI:** yes

## UX
_UX/UI Designer_
- **Mapa:** faixa acima do quadro, abaixo da faixa de desconectado/avisos, altura 40px, margem inferior 8px. Uma caixa por coluna, gap 2px, largura proporcional à da coluna (hoje todas 220dp, então iguais; o cálculo já usa a largura de cada uma). Cada caixa: raio 6px, superfície `panel`, borda `edge`, a contagem em Meta 13px `muted` tabular; coluna de decisão do dono (Spec Review, Staged) com a contagem em `sand` e a borda `sand`, como na lane (o acento continua só para o que espera o dono). Sem cor de estágio nesta entrega.
- **Contorno da janela:** retângulo de 2px `edgeHi` sobre as caixas, sem preenchimento, cobrindo a parte visível; não é interativo.
- **Clique:** na caixa rola o quadro até a coluna correspondente, sem animação (instantâneo; nada de movimento novo). Caixa focável com anel 2px `sand` offset 2px, `contentDescription` "Ir para <Coluna>, <N> cards" e alvo mínimo 24x24px.
- **Bordas que esmaecem:** faixa de 24dp em cada lado do quadro com gradiente de `ground` opaco para transparente, sobre o quadro, só quando há mais para ver naquele lado; não captura clique. Sem animação (aparece e some conforme a rolagem).
- **Quadro:** a área abaixo do mapa rola nos dois eixos. Cabeçalho da coluna (nome 17px/600 e contagem 13px) fixo no topo da coluna; os cards rolam por baixo; a coluna ocupa toda a altura da área, vazia ou não, com o texto "Vazia" no corpo.
- **Janela pequena:** mínimo 360x240. O mapa mantém a faixa inteira (cada caixa encolhe proporcionalmente, mínimo 12dp, sem texto de contagem se a caixa for mais estreita que 24dp; o `contentDescription` continua dizendo a contagem). As colunas mantêm 220dp.
- **Desconectado:** mapa e quadro esmaecidos (alpha .55) e sem clique, como o quadro hoje.
- **Leitor de tela:** o mapa é um grupo "Mapa das colunas"; cada caixa é um botão. O quadro mantém a região "Esteira".
- **Textos (pt-BR), no mesmo lugar único (`Texts`):** "Mapa das colunas", "Ir para <Coluna>, <N> cards".
- **Movimento:** nenhum novo; as bordas e a rolagem pelo mapa são instantâneas.

## Architecture
_Tech Lead_

**Contratos:** nenhum novo. O app continua só lendo `card.list` e `events.subscribe`; nada no daemon, no socket nem no banco muda.

**Camada e módulo:** só `app` (depende só de `core`). Arquivos:
- `app/src/main/kotlin/sfv2/app/BoardMap.kt` (novo): puro, sem IO nem Compose. `data class MapBox(column: Column, count: Int, start: Float, share: Float)` e `data class BoardMap(boxes: List<MapBox>, viewStart: Float, viewShare: Float)` com `start`/`share` em fração de 0..1 do total. `fun boardMap(board: Board, widths: Map<Column, Int>, gap: Int, viewportWidth: Int, scrollOffset: Int): BoardMap`; `fun scrollTarget(column: Column, widths: Map<Column, Int>, gap: Int, viewportWidth: Int): Int`; `fun edgeFades(scrollOffset: Int, maxScroll: Int): EdgeFades` (`data class EdgeFades(start: Boolean, end: Boolean)`, par de flags de estado, não escolhe comportamento). Largura total = soma das larguras + gaps; `maxScroll = max(0, total - viewportWidth)`.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: usa o `ScrollState` lateral já existente para ler `value`/`maxValue`, desenha o mapa (`ColumnMap`), as bordas esmaecidas e o cabeçalho fixo da coluna (cabeçalho fora do `verticalScroll`, `fillMaxHeight` na coluna). Clique chama `scrollState.scrollTo(scrollTarget(...))` num `scope.launch`. O mapa não desenha com `view == null`: mostra as 10 caixas com contagem 0 (AC-4).
- `app/src/main/kotlin/sfv2/app/Theme.kt`: os dois textos novos em `Texts` (`COLUMN_MAP`, `fun goToColumn(column, count)`).

**Teste:** a lógica de geometria é pura e vai em `BoardStateTest.kt` (a suíte do `Board` e do estado do quadro), sem Compose nem relógio. A renderização Compose não entra nos testes automáticos (padrão da esteira): cabeçalho fixo, altura total e estética são verificados à mão com captura em janela normal e mínima.

**Migração:** nenhuma. Sem log novo; nada de título nem payload em log.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

Testes em processo sobre as funções puras de `BoardMap.kt`, com `Board` das fixtures (`BoardFixtures`); sem sleeps. AC-6 é só de layout Compose e fica manual.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | manual | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`app`), mas `Touches UI` é `yes`.
- O texto da issue não veio pelas ferramentas (só o título); o escopo foi derivado do título e da seção "Target: the Board" do `DESIGN.md`. Redimensionar, recolher, densidades, rodapé de custo e painel lateral ficam fora e viram cards próprios.
- Geometria do mapa em funções puras com largura por coluna (`Map<Column, Int>`), para o redimensionamento futuro não mudar o contrato; hoje todas valem 220.
- Sem arquivo de teste novo: `BoardStateTest.kt` já cobre `Board` e o estado do quadro, e as funções novas operam sobre `Board`.
- Cabeçalho fixo e coluna de altura total ficam manuais (AC-6): são layout Compose, sem teste de UI na `app` hoje.
- Colunas de decisão do dono seguem `OWNER_DECISION_COLUMNS` do código (Spec Review e Staged); o acento do mapa reaproveita `isWaitingForOwner`.
- Rolagem pelo mapa instantânea: `DESIGN.md` limita o movimento contínuo a quatro itens e não prevê rolagem animada.

## Open questions
- none

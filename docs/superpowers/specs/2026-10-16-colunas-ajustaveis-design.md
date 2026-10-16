# Colunas ajustáveis, ajustar à janela e densidade — spec

**Issue:** #236 · **Branch:** `feat/236-colunas-ajustaveis` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** Todas as colunas do quadro têm a largura fixa de 220 dp (`LANE_WIDTH` em `BoardScreen.kt`). Em janela larga sobra espaço vazio à direita; em janela estreita o dono rola de lado mesmo com colunas vazias ocupando lugar; e não há como ver só o número de cartões de cada coluna. O alvo decidido em `DESIGN.md` ("Target: the Board") pede colunas redimensionáveis, ajuste à janela e duas densidades.
**Audience:** o dono, que acompanha o quadro das dez colunas no app desktop.
**Acceptance criteria**
- **AC-1** — Given uma coluna com a largura padrão, when o dono a redimensiona para um valor, then a largura vira esse valor limitado ao mínimo da densidade atual (200 dp em Cartões, 96 dp em Fichas) e ao máximo de 480 dp, e as demais colunas não mudam.
- **AC-2** — Given uma coluna redimensionada, when o dono dá duplo clique no puxador, then só essa coluna volta à largura padrão da densidade atual (220 dp em Cartões, 96 dp em Fichas).
- **AC-3** — Given colunas redimensionadas, when o dono aciona Equalizar, then todas as colunas voltam à largura padrão, sem alterar o ajuste à janela nem a densidade.
- **AC-4** — Given o ajuste à janela ligado (padrão) e uma janela mais larga que a soma das colunas, when a largura é calculada, then a sobra é dividida entre as colunas expandidas na proporção das suas larguras e a soma cabe exatamente na janela.
- **AC-5** — Given o ajuste à janela ligado e uma janela mais estreita que a soma das colunas, when a largura é calculada, then nenhuma coluna fica abaixo do mínimo da densidade e o quadro rola de lado; com o ajuste desligado nenhuma coluna cresce.
- **AC-6** — Given colunas sem cartões, when a largura é calculada, then cada uma vira uma faixa de 46 dp com a contagem e o nome na vertical, até o dono clicar nela para restaurá-la; uma coluna restaurada volta à sua largura e a faixa não conta no cálculo da sobra.
- **AC-7** — Given a densidade Fichas, when o quadro é desenhado, then cada coluna mostra o cabeçalho com nome e contagem e os cartões como fichas só com o número; em Cartões o desenho atual é mantido. Trocar a densidade preserva as larguras que o dono definiu para a outra densidade.
- **AC-8** — Given o puxador focado, when o dono aperta Esquerda ou Direita, then a coluna encolhe ou cresce 16 dp, respeitando os limites do AC-1; o minimapa e o rolar-até-a-coluna usam as larguras renderizadas.
**Out of scope:** guardar as larguras e a densidade entre sessões do app; rodapé da coluna com custo estimado; mudanças no painel lateral ou no pulso; alterar o protocolo do socket.
**Product fit:** é o alvo do quadro decidido pelo dono em 2026-10-16 (`DESIGN.md`, "Resizable columns" e "Two densities"); é só estado de tela, sem estado de fábrica.
**Touches UI:** yes

## UX
_UX/UI Designer_

- Puxador: pílula pequena sempre visível no canto do cabeçalho de cada coluna, mais clara no hover e no foco; caixa de pelo menos 24x24 px; foco com contorno `sand` de 2 px; semântica de botão com descrição "Ajustar largura de <coluna>".
- Barra acima do quadro, ao lado de Recarregar e Configurações: alternador "Ajustar à janela" (ligado por padrão), botão "Equalizar" e alternador de densidade "Cartões / Fichas". Todos com alvo de 24 px e rótulo textual.
- Coluna vazia recolhida: 46 dp, contagem em cima, nome na vertical, clicável e focável (Enter/Espaço restaura). Cores só de `Tokens`; nada fora do `DESIGN.md`.
- Em Fichas a ficha mostra o número do cartão na cor do estágio; o título fica na descrição de acessibilidade.
- Verificação visual: capturas em janela larga, estreita e com Fichas.

## Architecture
_Tech Lead_

Estado de tela puro e testável, sem IO nem relógio; o `Composable` só lê o estado e emite eventos. Nenhuma mudança em `core`, `store`, `daemon` nem no protocolo.

- `app/src/main/kotlin/sfv2/app/ColumnLayout.kt` (novo): `enum BoardDensity { CARDS, CHIPS }` com largura mínima e padrão; `data class ColumnLayoutState(widths, fit, density, restored)` imutável com `resize`, `reset`, `equalize`, `withFit`, `withDensity`, `restore`; `fun columnWidths(state, counts, viewportDp, gap): Map<Column, Int>` com recolhimento das vazias e distribuição proporcional da sobra.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: troca `LANE_WIDTH` fixo pelas larguras de `columnWidths`; puxador no cabeçalho (arrastar, duplo clique, setas); barra de controles; faixa recolhida; fichas.
- `app/src/main/kotlin/sfv2/app/BoardMap.kt` e `boardMap`/`scrollTarget`: sem mudança de código, já recebem o mapa de larguras.

Regras: booleano `fit` é aceito por ser estado de alternador, mas a densidade é enum; larguras em `Int` dp; estado guardado com `remember` na tela (sem persistência).

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-8 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`app`) e poucos caminhos, mas toca UI, o que exclui `light`.
- Os testes ficam em `BoardStateTest.kt`: é a suíte do módulo `app` para `boardMap`, `scrollTarget` e `SidePanelState.resize`, e a regra manda reaproveitar o arquivo existente.
- Largura máxima de 480 dp e passo de teclado de 16 dp: o `DESIGN.md` fixa só os mínimos (200 e 96 px) e a faixa de 46 px; o teto evita uma coluna maior que a janela.
- Largura padrão em Fichas de 96 dp (o mínimo): a ficha é só o número, não há razão para mais.
- Sem persistência entre sessões: o card não a pede e exigiria um lugar para guardar configuração do app; fica para um card próprio.
- Rodapé de custo estimado fica fora: é outro item do alvo do quadro, independente do redimensionamento.
- O corpo da issue não veio nas ferramentas (só o título); os ACs derivam do título e da seção "Target: the Board" do `DESIGN.md`.

## Open questions
- none

# Paleta de comandos Ctrl K — spec

**Issue:** #234 · **Branch:** `feat/234-paleta-de-comandos-ctrl-k` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** O dono chega a um card ou a uma tela do app só com o mouse, percorrendo dez colunas e abas. Falta um atalho de teclado que leve direto ao card (principalmente aos que esperam por ele) ou a uma tela. O alvo de interface (`DESIGN.md`, "The Palette" e a lista de componentes) já descreve a paleta.
**Audience:** o dono da fábrica, no app desktop.
**Acceptance criteria**
- **AC-1** — Given a paleta aberta com o campo vazio, when a lista é montada, then vêm primeiro os cards que esperam o dono (colunas de `OWNER_DECISION_COLUMNS`), depois as telas (Quadro, Feed, Configurações) e depois os demais cards, com no máximo 9 linhas.
- **AC-2** — Given um texto digitado, when a lista é filtrada, then entram as linhas cujo rótulo, título, `#id` (prefixo) ou nome de coluna contém o texto, sem diferenciar maiúsculas, mantendo a ordem do AC-1; sem nenhuma correspondência a lista fica vazia e o estado é "sem resultado", que não pode ser escolhido.
- **AC-3** — Given uma lista com linhas, when o dono usa seta para baixo/cima, Home ou End, then a linha ativa se move (com volta ao fim/início nas setas) e fica sempre dentro da lista, inclusive depois que o filtro encolhe a lista.
- **AC-4** — Given uma linha ativa, when o dono aperta Enter, then uma linha de card abre o detalhe daquele card e uma linha de tela troca para essa tela; a paleta fecha em seguida.
- **AC-5** — Given o app em qualquer tela, when o dono aperta Ctrl K (ou Cmd K), then a paleta abre; apertando de novo, Esc ou clicando no fundo, ela fecha e o texto digitado é descartado; enquanto está aberta, as teclas de uma letra do quadro (`M`) não agem.
**Out of scope:** ações que mudam estado (mover, aprovar) pela paleta; busca no texto da spec; histórico de comandos; endereço/hash (o app desktop não tem).
**Product fit:** acelera o ciclo "esperar o dono" de PRODUCT.md: o dono chega ao card parado sem caçar a coluna.
**Touches UI:** yes

## UX
_UX/UI Designer_

Segue `DESIGN.md` ("The Palette"), adaptado ao desktop Compose:
- Caixa no topo centralizado, largura `min(560dp, janela - 32dp)`, fundo `Tokens.raise`/painel alto, borda de 1dp `Tokens.edgeHi`, raio 14dp, sobre um fundo (scrim) que fecha ao clicar.
- Um campo de texto (15sp) com foco automático ao abrir; abaixo, até 9 linhas.
- Linha de card: `#id` em mono (cor `Tokens.sand` quando espera o dono, senão `Tokens.muted`), título em uma linha com reticências, coluna em 12sp `Tokens.muted`. Linha de tela: só o rótulo.
- Linha ativa com fundo `Tokens.raise` e borda esquerda de 2dp `Tokens.text`.
- Sem resultado: uma linha `Tokens.muted` ("Nenhum resultado"), não selecionável, em região viva polida.
- Semântica: o campo anuncia a linha ativa; o foco volta ao ponto anterior ao fechar.
- Sem animação própria.

## Architecture
_Tech Lead_

Só a camada `app`; sem protocolo novo, sem leitura de banco, sem evento. A paleta usa o `BoardView` que o app já tem.
- `app/src/main/kotlin/sfv2/app/PaletteModel.kt` (novo): lógica pura, sem Compose. `PaletteEntry` (sealed: `Card(BoardCard, waiting)` e `View(PaletteView)`), enum `PaletteView { BOARD, FEED, SETTINGS }`, `PaletteState(open, query, active)` imutável com `toggle()`, `close()`, `withQuery()`, `move(Direction)`, e `fun paletteEntries(cards, views, query): List<PaletteEntry>` com o limite `PALETTE_ROWS = 9`.
- `app/src/main/kotlin/sfv2/app/PaletteScreen.kt` (novo): composable fino que desenha o estado e repassa teclas ao modelo; o título do card é texto externo, desenhado como texto simples, nunca logado.
- `app/src/main/kotlin/sfv2/app/Main.kt`: guarda o `PaletteState`, captura Ctrl K/Cmd K com `onPreviewKeyEvent` na janela (antes das teclas de uma letra) e liga Enter a `openCardId`, `showFeed`, `showSettings`.
- `app/src/main/kotlin/sfv2/app/Theme.kt`: textos da paleta em `Texts` (pt-BR).
- `BoardScreen` não muda: a inércia do `M` vem de `Main` não entregar a tecla (o evento de pré-visualização da janela consome tudo enquanto a paleta está aberta).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Ordem da lista vazia: cards que esperam o dono, telas, demais cards. `DESIGN.md` diz "actions first" na seção da paleta e "cards first those that wait for the owner, then the views" na lista de componentes; fica a segunda porque serve o objetivo (chegar ao que espera o dono) e a lista de componentes é a mais recente.
- Testes em `BoardStateTest.kt`, sem arquivo novo: a paleta é derivada do `Board` e a suíte já cobre o estado do quadro; a fixture `BoardFixtures` é reaproveitada.
- Lógica da paleta fica em `PaletteModel.kt` puro, para ser testada sem Compose; a tecla e o desenho ficam finos e sem teste de UI.
- Sem hash/endereço: o app desktop não tem barra de endereço, então a parte "Moving through the palette pushes nothing" não se aplica.
- Ações de mudança de estado ficam fora: elas passam por `OwnerMoveController`, com nota e eventos assinados, e merecem card próprio.
- Título de card é dado não confiável: só desenhado como texto, nunca logado nem interpretado.

## Open questions
- none

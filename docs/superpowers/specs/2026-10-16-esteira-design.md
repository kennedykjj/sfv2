# Esteira — spec

**Issue:** #64 · **Branch:** `feat/64-esteira` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** o dono não vê o que a fábrica está fazendo nem o que espera por ele sem abrir a CLI; falta a visão principal do app, com colunas e cards ao vivo.
**Audience:** o desenvolvedor sozinho com muitos projetos (e o que está numa empresa), que precisa ver de relance onde a fábrica parou para ele.
**Acceptance criteria**
- **AC-1** — Given o app conectado ao daemon, when a esteira abre, then ela mostra as 10 colunas na ordem do produto (Inbox ... Done), cada uma com os cards do snapshot `card.list` na coluna correta.
- **AC-2** — Given a esteira aberta, when chega um evento `card.created`, then o card novo aparece na coluna indicada sem recarregar.
- **AC-3** — Given a esteira aberta, when chega um evento `card.moved`, then o card sai da coluna de origem e aparece na de destino sem recarregar.
- **AC-4** — Given o daemon sem nenhum card, when a esteira abre, then as 10 colunas aparecem vazias, com um estado vazio legível, sem erro.
- **AC-5** — Given cards em Spec Review ou Ready for Prod (decisões do dono), when a esteira é exibida, then esses cards se destacam com a regra de acento do DESIGN.md e os demais não.
- **AC-6** — Given um card em destaque, when ele é movido para fora de uma coluna de decisão, then o destaque some.
- **AC-7** — Given a conexão com o daemon cai e volta, when a assinatura é retomada, then a esteira reflete o estado atual sem perder nem duplicar eventos ocorridos durante a queda.
- **AC-8** — Given a conexão caída, when a esteira é exibida, then ela indica que está desconectada em vez de mostrar dados como se fossem ao vivo.
**Out of scope:** mover cards pelo dono (#65); visão detalhada do card (#66); filtros, busca e múltiplos projetos além do que o snapshot já traz; escrita de qualquer tipo no daemon.
**Product fit:** serve ao princípio de que o dono aparece só onde a decisão é dele (destaque de Spec Review e Ready for Prod) e à fábrica dona do próprio estado (o app é só cliente do daemon). Não contradiz o posicionamento: não é board de time, é leitura do estado local.
**Touches UI:** yes

## UX
_UX/UI Designer_
- **Tela:** a esteira ocupa a janela abaixo do indicador de conexão da casca (reaproveitado, sem componente novo). Fundo `ground`; 10 colunas lado a lado na ordem do produto (Inbox, Ready for Refinement, Refinement, Spec Review, Ready for Dev, Coding, Validation, Staged, Ready for Prod, Done), cada uma com 220px de largura, gap 8px, padding da tela 16px. Somente leitura: sem arrastar, sem botão, sem menu.
- **Coluna:** superfície `panel`, borda `edge`, raio 12px (`tile`). Cabeçalho com o nome em inglês (Headline 17px/600, `text`) e a contagem em Meta 13px `muted`, tabular ("3"). Cards empilhados, o mais antigo primeiro, rolagem vertical dentro da coluna.
- **Card:** faixa de 1 linha (raio 8px, borda `edge`, fundo `ground`): o id em IBM Plex Mono 12px `muted` e o título em Body 14px `soft`, texto puro, truncado com reticências (`maxLines = 1`, `TextOverflow.Ellipsis`); o título completo nunca é interpretado como markup nem instrução. Sem número de card.
- **Destaque de decisão (Spec Review, Ready for Prod):** só o acento `sand`, nunca outra cor: borda da coluna `sand`, nome da coluna em `sand`, e o id dos cards em `sand`; os demais ficam em `edge`/`muted`. O destaque vem da coluna, então some sozinho quando o card sai dela (AC-6). Coluna de decisão vazia não é destacada. O estado nunca vai só por cor: a coluna de decisão também diz "Aguarda você" em Meta 13px `sand` abaixo do nome.
- **Estados:**
  - Carregando (conectado, sem snapshot): colunas já desenhadas com a contagem "–" e a linha "Carregando a esteira…" no lugar dos cards, sem esqueleto.
  - Vazio (AC-4): as 10 colunas aparecem; cada uma mostra "Vazia" em Meta `faint`; acima delas, uma linha única em Body `soft`: "Nenhum card na fábrica ainda." Sem erro, sem `warn`.
  - Coluna vazia com outras cheias: só "Vazia" em `faint`.
  - Desconectado (AC-8): o último quadro permanece visível, esmaecido (alpha .55), sem interação; faixa no topo em `warn` (borda e ponto `warn`, texto `text`, raio 12px): "Desconectado do daemon. Mostrando o último estado conhecido; pode estar desatualizado." Nunca `bad`, nunca `sand`. Se nunca houve snapshot, a faixa aparece sobre o estado de carregamento. Ao reconectar (AC-7) a faixa some e o quadro volta à opacidade total sem aviso extra.
  - Card novo ou movido (AC-2/3): aparece/sai na hora, sem animação, sem rolagem automática nem roubo de foco.
- **Janela pequena:** mínimo 360x240. As colunas mantêm 220px e a esteira rola na horizontal dentro da janela (cada coluna rola na vertical sozinha); a faixa de desconectado fica fixa no topo, fora da rolagem, quebrando linha. Sem recolher colunas nem trocar de layout.
- **Teclado e leitor de tela:** a esteira é uma região "Esteira" (`role="region"`); cada coluna é um grupo rotulado "<Coluna>, <N> cards" (ex.: "Spec Review, 2 cards, aguarda você") e os cards uma lista, cada item lido como "<título>, <id>". Como a tela é só de leitura, não há alvos de foco por card; a única parada de tab é a própria região (rolável), que aceita setas e Home/End para rolar. A faixa de desconectado é `role="status"` (`aria-live="polite"`). Foco visível: contorno 2px `sand` com offset 2px.
- **Textos (pt-BR):** nomes de colunas ficam em inglês. Novos: "Aguarda você", "Vazia", "Nenhum card na fábrica ainda.", "Carregando a esteira…", "Desconectado do daemon. Mostrando o último estado conhecido; pode estar desatualizado.", rótulos de leitor de tela "Esteira", "<N> cards" / "1 card". Ficam no mesmo lugar único de textos da casca.
- **Tipografia e movimento:** IBM Plex Sans (Mono só no id), sem texto abaixo de 12px, números tabulares. Sem animação nova; a esteira não tem movimento contínuo.

## Architecture
_Tech Lead_

**Contratos (nenhum novo no daemon; o app só lê):**
- `{"cmd":"card.list"}` → `{"ok":true,"result":[{"id","title","column"}]}`, com `column` o nome do enum `Column` (`SPEC_REVIEW`).
- `{"cmd":"events.subscribe"}` ou `{"cmd":"events.subscribe","after":<seq>}` → `{"ok":true,"result":"subscribed"}` e, daí em diante, uma linha por evento `{"event":{"seq","card","type","payload":{...},...}}`. O app usa `CARD_CREATED` (`payload.title`; a coluna inicial é Inbox, como em `CardCommands.create`) e `CARD_MOVED` (`payload.to` = nome do enum `Column`); os demais tipos avançam só o cursor `seq`. Linha `{"error":"subscriber too slow"}` ou conexão fechada = reconectar com `after`.
- Os `type` dos eventos são os nomes do enum `EventType` (`CARD_MOVED`), não `card.moved` como nos critérios.

**Camada e módulo:** só `app` (depende só de `core`). Arquivos:
- `app/build.gradle.kts`: plugin `kotlin("plugin.serialization")` e `libs.kotlinx.serialization.json` (já no catálogo, versão fixa) para ler as linhas JSON; sem dependência nova no catálogo.
- `app/src/main/kotlin/sfv2/app/BoardState.kt`: puro, sem IO. `data class BoardCard(id, title, column: Column)`; `data class Board(cards, lastSeq: Long?)` imutável com `applySnapshot(cards)`, `applyEvent(BoardEvent)` (idempotente: `CARD_MOVED` põe o card em `to`, `CARD_CREATED` faz upsert na Inbox, card desconhecido num move é ignorado, `seq` menor ou igual ao `lastSeq` é descartado), `cardsIn(column)`; `val OWNER_DECISION_COLUMNS = setOf(SPEC_REVIEW, READY_FOR_PROD)` e `fun isWaitingForOwner(column)`.
- `app/src/main/kotlin/sfv2/app/BoardSource.kt`: port `interface BoardSource { fun snapshot(): List<BoardCard>; fun subscribe(after: Long?, onEvent: (BoardEvent) -> Unit) }` (`subscribe` bloqueia até a conexão cair e lança `IOException`), `BoardEvent(seq, cardId, type, payload)`, e `SocketBoardSource` (socket Unix `$XDG_RUNTIME_DIR/sfv2/daemon.sock`, uma conexão por chamada, como o `SocketDaemonProbe`).
- `app/src/main/kotlin/sfv2/app/BoardMonitor.kt`: sem UI. Recebe `BoardSource`, o `StateFlow<ConnectionState>` do `ConnectionMonitor` e um dispatcher/scheduler injetado. Quando `Connected`: assina primeiro (sem `after`, ou com o `lastSeq` já visto), depois lê o `snapshot` e o aplica antes dos eventos (último evento por card vence, então a corrida entre os dois é inofensiva); ao cair a assinatura, marca `live=false`, espera o próximo `Connected` e reassina com `after=lastSeq`, sem recarregar nem duplicar. Expõe `StateFlow<BoardView(board: Board, live: Boolean)>`; antes do primeiro snapshot não há `BoardView`.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: Compose, renderiza `BoardView` conforme `## UX` (tokens do `DESIGN.md`; destaque `sand` só em `isWaitingForOwner`).
- `app/src/main/kotlin/sfv2/app/Main.kt`: `Connected` com `BoardView` mostra a esteira; sem `BoardView` ainda mostra o `ConnectionScreen`; `Disconnected` com `BoardView` mantém a esteira (esmaecida, faixa de desconectado).

**Migração:** nenhuma. Sem escrita no daemon nem no banco; o app não loga título nem payload, só ids e `seq` (CLAUDE.md).

**Verificação visual:** a renderização Compose não entra nos testes automáticos; o destaque e o estado desconectado são verificados à mão na revisão (captura em janela normal e mínima). A lógica de destaque (`isWaitingForOwner`) é testada.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

Testes em processo, sem janela: `BoardState` puro e `BoardMonitor` com `BoardSource` falso e `ConnectionState` controlado; sem sleeps.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |
| AC-8 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`app`), mas mais de 3 critérios.
- Colunas de decisão do dono = Spec Review e Ready for Prod, como em `PRODUCT.md`; ficam numa constante só (`MoveRules` marca Staged→Ready for Prod como do dono, então trocar o conjunto é uma linha se o dono preferir).
- Assinar antes de ler o snapshot e aplicar os eventos depois: o `card.list` não traz `seq`, e eventos são absolutos (`to`), então "último evento vence" fecha a corrida sem perda.
- Reconexão retoma por `after=lastSeq` (S-stream, AC-2 do #17) em vez de recarregar o snapshot: não perde nem duplica eventos.
- Arquivos de teste novos `BoardStateTest.kt` e `BoardMonitorTest.kt`: a `app` só tem `ConnectionMonitorTest`, `ModuleTest` e `PopupSkinTest`, nenhum cobre a esteira.
- Título do card é texto externo não confiável: exibido como texto puro e nunca logado.
- A esteira mostra títulos de card; isso substitui o "sem texto de issue" do AC-6 do #63, que valia para a tela de conexão.
- Destaque no nível da coluna (borda, nome, "Aguarda você" e id dos cards em `sand`): o critério fala em cards que se destacam, e assim eles se destacam sem estado por card; some ao sair da coluna.
- Ordem dos cards na coluna: a do snapshot (`card.list`), com card novo ao fim (o mais antigo primeiro, como no UX); `Board` preserva a ordem de inserção e um card movido vai ao fim da coluna de destino.
- O id mostrado no card é o prefixo de 8 caracteres do UUID (o id inteiro não cabe na faixa de 1 linha).
- Dependência no #62 (aberta) não bloqueia: `card.list` e `events.subscribe` já estão na main.

## Open questions
- none

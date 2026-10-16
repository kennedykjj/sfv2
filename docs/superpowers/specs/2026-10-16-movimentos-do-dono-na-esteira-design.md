# Movimentos do dono na esteira — spec

**Issue:** #65 · **Branch:** `feat/65-movimentos-do-dono-na-esteira` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** a esteira do app é só leitura; para aprovar a spec, liberar a entrega ou mandar um card de volta, o dono precisa sair do app e usar a CLI. As decisões do dono têm que ser feitas na própria esteira, por arrastar ou por teclado, com nota quando pedem a mudança.
**Audience:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa (o dono da fábrica), nas decisões de Spec Review e Ready for Prod.
**Acceptance criteria**
- **AC-1** — Dado um card numa coluna com movimento permitido ao dono (Inbox→Ready for Refinement, Spec Review→Ready for Dev, Spec Review→Ready for Refinement, Staged→Ready for Prod), quando o dono o arrasta até a coluna de destino, então o app envia o comando ao daemon com a prova do dono e o card aparece na coluna nova na hora.
- **AC-2** — Dado um card focado, quando o dono aperta M, então abre-se a escolha de destinos com só os movimentos permitidos para aquele card; setas mudam o destino, Enter confirma e envia, Esc cancela sem enviar nada.
- **AC-3** — Dado um card em Spec Review, quando o dono escolhe devolvê-lo a Ready for Refinement (arrastando ou pelo teclado), então o app pede uma nota obrigatória antes de enviar; sem nota o envio não acontece, e com nota o card volta com ela registrada.
- **AC-4** — Dado um movimento que o daemon recusa, quando a resposta chega, então o card volta à coluna de origem e o motivo da recusa aparece perto do card, em texto legível.
- **AC-5** — Dado um card que não tem movimento permitido ao dono (por exemplo, em Coding), quando o dono tenta arrastá-lo ou aperta M, então nenhum destino é oferecido e nada é enviado.
- **AC-6** — Dado que o daemon está indisponível ou o app não acha a prova do dono, quando o dono tenta mover um card, então o card volta à origem e o app diz que o movimento não foi enviado.
**Out of scope:** reordenar cards dentro da coluna (o daemon não tem o comando); movimentos feitos pelos agentes ou pelos gates; desfazer um movimento já aceito; mover vários cards de uma vez; outros movimentos além dos quatro permitidos ao dono.
**Product fit:** serve o princípio de que o dono aparece só onde a decisão é dele (aprovar a spec, liberar a entrega) e de que toda ação é um evento com ator (o app usa a prova do dono, e o daemon continua o único escritor e juiz da regra). Não contradiz o posicionamento nem o que está fora de escopo.
**Touches UI:** yes

## UX
_UX/UI Designer_

Tela: `BoardScreen`, lanes e `Card` existentes, tokens do DESIGN.md (`edge-hi`, `sand`, `bad`, `warn`, `faint`, `panel-hi`, `raise`); nenhum componente novo além da caixa de nota e do seletor de destino. Idioma do app: pt-BR; nomes de coluna ficam em inglês. Sem reordenação e sem Undo.

- **Pegar (arrastar):** só card com movimento permitido tem `grab`; ao pegá-lo, contorno 2px `edge-hi` no card, contorno tracejado 1px `edge-hi` nas lanes que o aceitam (as outras caem a .45) e, nelas, botão ghost com o significado: "Solte para aprovar a spec" (Spec Review→Ready for Dev), "Solte para devolver à refinagem" (→Ready for Refinement), "Solte para liberar a entrega" (Staged→Ready for Prod), "Solte para enviar à refinagem" (Inbox→Ready for Refinement). Soltar fora de lane válida devolve o card sem enviar nada.
- **Teclado:** cada card é focável (Tab/setas entre cards; foco com anel `edge-hi`). `M` no card focado abre o seletor de destinos: lista curta, uma opção por destino permitido (mesmos textos do ghost acima, sem "Solte para"), ← → ou ↑ ↓ trocam, Enter confirma e envia, Esc cancela sem enviar. Foco volta ao card ao fechar.
- **Sem movimento permitido (AC-5):** card em Coding etc. tem `not-allowed` e cadeado 12px com o motivo ("Em execução", "Bloqueado por infraestrutura"); arrastar não levanta o card; `M` não abre o seletor e a linha de status diz "Este card não tem movimento seu."
- **Linha de status** (`role="status"`, sob o título, 13px `muted`): "Segurando #12. Setas escolhem o destino, Enter confirma, Esc cancela." / "Nada enviado." quando cancelado.
- **Nota (AC-3):** só Spec Review→Ready for Refinement; abre a caixa de nota (superfície `raise`, raio 8px, sob a lane de destino) com campo multilinha focado, rótulo "Nota para a refinagem", botões "Devolver" (desabilitado enquanto a nota estiver vazia ou só espaços, `aria-disabled`) e "Cancelar". Enter envia, Shift+Enter quebra linha, Esc fecha e devolve o card, sem enviar. Aprovar e liberar não pedem nota.
- **Otimista (AC-1):** o card sai da origem e aparece no destino na hora, com `sand` apagando em 1 s se a lane de destino aguarda o dono; sem toast de Desfazer. Pedido em voo mantém o card sem arrastar de novo.
- **Recusa (AC-4):** o card volta à lane de origem e a linha de nota do painel (12px `bad`, `liveRegion` assertivo, sob o título da lane de origem) diz o motivo do daemon em texto simples, prefixado "Não movido: ". Some ao próximo movimento ou após fechar por "Entendi" (link `.lnk`).
- **Sem envio (AC-6):** daemon fora do ar ou prova do dono ausente: card volta à origem e a nota, em `warn`, diz "Movimento não enviado: o daemon está indisponível." ou "Movimento não enviado: prova do dono não encontrada." Com a faixa de desconectado visível, arrastar e `M` seguem permitidos e caem nesta mensagem.
- **Carregando/vazio:** sem snapshot ("Carregando…") ou lane vazia ("Vazia") nada é arrastável; lane vazia ainda aceita soltura.
- **Janela estreita:** o quadro já rola na horizontal; durante o arrasto a rolagem automática nas bordas; o seletor `M` e a caixa de nota abrem como painel `raise` ancorado ao card/lane, com largura máxima 320dp e rolagem se faltar altura.
- **Acessibilidade:** contentDescription do card inclui "pode mover" ou o motivo do bloqueio; cor nunca é o único sinal (texto + cadeado); movimento reduzido: sem fade, o card só pula.

## Architecture
_Tech Lead_

Camada única: `app`. O daemon e o `core` não mudam (`card.move`, `card.approve`, `card.request-change` e `MoveRules` já existem).

- **Destinos do dono** (`app/src/main/kotlin/sfv2/app/OwnerMoves.kt`, novo): `ownerTargets(from: Column): List<Column>` = destinos de `MoveRules.table` com `MoveAuthority.OWNER_ONLY` a partir de `from` (os quatro movimentos do AC-1); vazio quando não há. `needsNote(from, to)` é verdadeiro só em Spec Review→Ready for Refinement.
- **Resultado** (`OwnerMoves.kt`): `sealed interface MoveOutcome { Accepted; Refused(reason: String); NotSent(cause: NotSentCause) }`, `enum NotSentCause { DAEMON_UNAVAILABLE, NO_OWNER_PROOF }`.
- **Porta** (`OwnerCommands.kt`, novo): `interface OwnerCommands { fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome }`. `SocketOwnerCommands(socket: Path, token: Path)` lê o `owner.token` ao lado do socket a cada envio (nunca guarda nem loga) e escolhe o comando: Spec Review→Ready for Dev = `card.approve`; Spec Review→Ready for Refinement = `card.request-change` com `note`; demais = `card.move`; todos com `actor=owner` e `proof`. Resposta `error` do daemon vira `Refused(message)`; `IOException` vira `DAEMON_UNAVAILABLE`; token ausente ou ilegível vira `NO_OWNER_PROOF`. A montagem da requisição é uma função pura testável.
- **Estado otimista** (`BoardState.kt`): `Board.withOptimisticMove(cardId, to)` e `Board.revertMove(cardId, from)`; o evento do daemon que chega depois é idempotente (`moveTo` é absoluto).
- **Controlador** (`OwnerMoves.kt`): `OwnerMoveController(commands: OwnerCommands, io: CoroutineDispatcher)` expõe o estado (card seguro, nota pendente, linha de status); `hold`, `chooseTarget`, `submit(note)`, `cancel`. Aplica o movimento otimista, envia em `io`, reverte em `Refused`/`NotSent` e publica a mensagem do UX. Sem nota não envia (AC-3). Card sem destino não é pego (AC-5).
- **UI** (`BoardScreen.kt`, `Main.kt`): arrastar, `M`/setas/Enter/Esc, seletor, caixa de nota e linha de status conforme `## UX`; `Main.kt` monta `SocketOwnerCommands` com o socket e o `owner.token` do mesmo diretório.
- **Arquivos:** `OwnerMoves.kt`, `OwnerCommands.kt`, `BoardState.kt`, `BoardScreen.kt`, `Main.kt` (todos na camada `app`).
- **Migração:** nenhuma; sem mudança de contrato nem de dados.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Escopo limitado aos quatro movimentos owner-only de MoveRules; reordenar fica fora porque o daemon não tem o comando.
- Arquivo de teste novo `OwnerMovesTest.kt`: nenhuma suíte do app cobre movimentos do dono; AC-4 (reversão do estado otimista) fica em `BoardStateTest.kt`, que já cobre `Board`.
- Camada única `app`: os destinos do dono saem de `MoveRules.table` (já pública), sem tocar o `core`.
- O app não conhece estado de execução do card, então o cadeado com motivo ("Em execução") do UX não entra; card sem movimento do dono só mostra "Este card não tem movimento seu."
- Aprovar usa `card.approve` e devolver usa `card.request-change`, os comandos do daemon para Spec Review, em vez de `card.move`.
- A nota é obrigatória só ao devolver de Spec Review para Ready for Refinement, como o daemon já exige.

## Open questions
- none

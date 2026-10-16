# Movimentos do dono na esteira — plano (#65)

Camada única: `app` (peso standard). O `core` e o daemon não mudam.

## Contrato (o que QA testa e o engineer segue)

`OwnerMoves.kt` (puro, sem Compose):
- `ownerTargets(from: Column): List<Column>` — destinos de `MoveRules.table` com `OWNER_ONLY` a partir de `from`; vazio se não há.
- `needsNote(from: Column, to: Column): Boolean` — só `SPEC_REVIEW → READY_FOR_REFINEMENT`.
- `sealed interface MoveOutcome { data object Accepted; data class Refused(val reason: String); data class NotSent(val cause: NotSentCause) }`, `enum class NotSentCause { DAEMON_UNAVAILABLE, NO_OWNER_PROOF }`.
- `interface BoardEdits { fun board(): Board?; fun edit(transform: (Board) -> Board) }` — o monitor implementa; testes usam um fake com `Board` em memória.
- `data class OwnerMoveState(val held: BoardCard?, val targets: List<Column>, val selected: Column?, val notePending: Boolean, val status: String?, val notice: MoveNotice?)`; `data class MoveNotice(val kind: NoticeKind, val text: String)`; `enum class NoticeKind { REFUSED, NOT_SENT }`.
- `class OwnerMoveController(commands: OwnerCommands, edits: BoardEdits, io: CoroutineDispatcher)` com `val state: StateFlow<OwnerMoveState>`:
  - `hold(cardId)`: pega o card; sem destinos (AC-5) não pega, `held=null`, `status = Texts.NO_OWNER_MOVE` ("Este card não tem movimento seu.") e nada é enviado.
  - `chooseTarget(to)`: escolhe um destino de `targets` (ignora outro).
  - `suspend submit(note: String? = null)`: sem destino escolhido não faz nada; se `needsNote` e nota nula/em branco não envia (AC-3) e `notePending=true`; senão aplica `Board.withOptimisticMove`, envia em `io` via `commands.move(cardId, from, to, note?.trim())`, limpa `held`; `Accepted` mantém o card movido; `Refused(reason)` reverte com `Board.revertMove` e publica `notice = REFUSED, "Não movido: <reason>"`; `NotSent(DAEMON_UNAVAILABLE)` / `NotSent(NO_OWNER_PROOF)` revertem e publicam `NOT_SENT`, "Movimento não enviado: o daemon está indisponível." / "Movimento não enviado: prova do dono não encontrada.".
  - `cancel()`: solta o card sem enviar, `status = "Nada enviado."`.
  - `dismissNotice()`.

`BoardState.kt`: `Board.withOptimisticMove(cardId, to)` (card vai para o fim de `to`) e `Board.revertMove(cardId, from, optimistic)` (só desfaz se o card ainda está em `optimistic`; senão devolve o mesmo board).

`OwnerCommands.kt`: `interface OwnerCommands { fun move(cardId: String, from: Column, to: Column, note: String?): MoveOutcome }`; `fun ownerMoveRequest(cardId, from, to, note, proof): String` (pura): SPEC_REVIEW→READY_FOR_DEV = `card.approve`; SPEC_REVIEW→READY_FOR_REFINEMENT = `card.request-change` com `note`; demais = `card.move` com `to`; todos com `actor=owner` e `proof`. `SocketOwnerCommands(socket: Path, token: Path)`: lê o token a cada envio (ausente/ilegível/vazio → `NotSent(NO_OWNER_PROOF)`), `IOException` → `NotSent(DAEMON_UNAVAILABLE)`, `ok:false` → `Refused(error)`.

## Tarefas (todas na camada `app`, dono: engineer)

1. `BoardState.kt`: `withOptimisticMove` e `revertMove`. Arquivos: `app/src/main/kotlin/sfv2/app/BoardState.kt`.
2. `OwnerMoves.kt` e `OwnerCommands.kt` (regras, resultado, controlador, porta e socket). Depende de 1.
3. UI: `BoardMonitor` implementa `BoardEdits`; `BoardScreen.kt` (arrastar, M/setas/Enter/Esc, seletor, caixa de nota, linha de status, nota de recusa) e `Main.kt` (monta `SocketOwnerCommands` com `owner.token` ao lado do socket). Depende de 2.

Testes: `OwnerMovesTest.kt` (novo, AC-1/2/3/5/6) e `BoardStateTest.kt` (AC-4), ambos em `app/src/test/kotlin/sfv2/app/`.

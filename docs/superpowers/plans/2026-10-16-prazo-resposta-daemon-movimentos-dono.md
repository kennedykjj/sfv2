# Prazo na resposta do daemon nos movimentos do dono — plano

1. `OwnerMoves.kt`: adicionar `NO_ANSWER` a `NotSentCause`.
2. `Theme.kt`: ramo `NO_ANSWER` em `Texts.notSent` ("Movimento sem resposta do daemon: confira o quadro antes de repetir.").
3. `OwnerCommands.kt`: `timeoutMillis` (padrão 5 000) em `SocketOwnerCommands`; leitura da resposta em `CompletableFuture` com prazo, canal fechado no `finally`; `TimeoutException` vira `NotSent(NO_ANSWER)`.
4. `OwnerMovesTest.kt`: teste do daemon mudo (AC-1), da resposta dentro do prazo (AC-2), do aviso e da reversão no controller (AC-3) e dos caminhos `DAEMON_UNAVAILABLE`/`NO_OWNER_PROOF` intactos (AC-4). Sem `Thread.sleep`.
5. `./gradlew :app:test` e `./gradlew build`.

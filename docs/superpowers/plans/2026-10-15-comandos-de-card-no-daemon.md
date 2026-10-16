# Comandos de card no daemon — plano (#16)

Spec: `docs/superpowers/specs/2026-10-15-comandos-de-card-no-daemon-design.md`

## Tarefas

1. **store** (agente: engineer) — sem dependências.
   - `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: query `allCards` ordenada por rowid.
   - `store/src/main/kotlin/sfv2/store/CardEventStore.kt`: `cards(): List<StoredCard>`.
2. **daemon** (agente: engineer) — depende da tarefa 1.
   - `daemon/.../Protocol.kt`: requisições e respostas `card.create`, `card.move`, `card.list`, `card.show`.
   - `daemon/.../CardCommands.kt` (novo): aplica `MoveRules`, gera id e `EventId` UUID, recebe `Clock` e `CardEventStore`; recusa não escreve nada.
   - `daemon/.../SocketServer.kt`: recebe `CardCommands` e despacha.
   - `daemon/.../Main.kt`: abre `Database` do caminho de dados e injeta.
   - Sem log de `title` nem payload: só id do card e tipo do comando.

## Testes

Escritos antes pelo QA: `SocketServerTest.kt` (daemon) e `ModuleTest.kt` (store).

# Plano: gravar e publicar evento sob um único lock (#134)

Spec: `docs/superpowers/specs/2026-10-16-gravar-e-publicar-evento-sob-um-unico-lock-design.md`

## Tarefas

Todas na camada `daemon`, agente `engineer` (execução solo pelo Tech Lead, peso standard).

1. **EventRecorder** — novo `daemon/src/main/kotlin/sfv2/daemon/EventRecorder.kt` com `record(cardId, card, event): StoredEvent`, `@Synchronized`, chamando `store.recordChange` e `events.publish`.
2. **CardCommands** — criar `val recorder` público; `create` e `move` usam `recorder.record`.
3. **RunPool** — trocar `onEvent` por `recorder: EventRecorder`; `recordRelease` usa o recorder.
4. **GateExecutor** — trocar `events: EventStream` por `recorder: EventRecorder`.

Dependências: 1 antes de 2, 3 e 4 (independentes entre si).

## Testes (QA)

`EventRecorderTest.kt` (AC-1, AC-2, AC-3); `RunPoolTest` e `GateExecutorTest` ajustam só o construtor.

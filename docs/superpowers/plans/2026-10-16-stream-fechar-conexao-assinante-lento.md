# Stream: fechar a conexão do assinante lento — plano

**Issue:** #122 · **Spec:** `docs/superpowers/specs/2026-10-16-stream-fechar-conexao-assinante-lento-design.md`

## Tarefas

### T1 — daemon (agente: engineer, feito direto pelo Tech Lead: peso standard, uma camada)
Sem dependências.

- `daemon/src/main/kotlin/sfv2/daemon/EventStream.kt`
  - construtor ganha `clock: Clock = Clock.systemUTC()` e `closeGrace: Duration = DEFAULT_CLOSE_GRACE` (5 s); `require(!closeGrace.isNegative)`.
  - ao marcar `dropped`, o assinante guarda `droppedAt = clock.instant()` e entra numa lista `slowDropped`.
  - `internal fun sweep()`: para cada assinante em `slowDropped` com `clock.instant() >= droppedAt + closeGrace`, chama `finish()` (fecha o canal, interrompe o `write` bloqueado, roda `onClosed`).
  - `finish()` idempotente e remove o assinante de `slowDropped`.
  - thread virtual que chama `sweep()` a cada `POLL_MILLIS`; para em `close()` do `EventStream`.
- `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt`: repassa `clock` e um `closeGrace` ao construir o `EventStream`.
- `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt`: acceptance tests (QA).

## Ordem
1. QA escreve os testes de aceitação (falhando).
2. T1 implementa até passarem.

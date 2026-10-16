# CLI de cards — plano

**Issue:** #18 · **Spec:** `docs/superpowers/specs/2026-10-16-cli-de-cards-design.md`

## Tarefas

1. **cli** (engineer) — implementar o cliente do daemon e os comandos.
   - `cli/build.gradle.kts`: `implementation(libs.kotlinx.serialization.json)`.
   - `SocketLocation.kt`, `OwnerToken.kt`, `DaemonClient.kt`, `CardCommands.kt`.
   - `Main.kt`: `runCli` ganha `env`; registra `card` (create, list, move, show) e `events`.
   - Depende dos testes de aceitação (QA) em `MainTest.kt` e `CardsCliTest.kt`.

Não há tarefa em `core`, `store` ou `daemon`. A camada `e2e` só tem o teste de QA.

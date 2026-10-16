# Casca do app e conexão ao daemon — plano

**Issue:** #63 · **Spec:** `docs/superpowers/specs/2026-10-16-casca-do-app-e-conexao-ao-daemon-design.md`

## Tarefas

Todas na camada `app` (agente: techlead solo, peso standard; `gradle/libs.versions.toml` não tem camada). Dependência entre elas: 1 → 2 → 3.

1. **Lógica de conexão (sem UI)** — `app/src/main/kotlin/sfv2/app/ConnectionState.kt`, `DaemonProbe.kt`, `ConnectionMonitor.kt`.
   `ConnectionState`, `DisconnectReason`, ports `DaemonProbe`/`DaemonStarter`, `ConnectionMonitor` com `Clock` e scheduler injetados, `StateFlow<ConnectionState>`.
2. **Implementações reais** — `DaemonProbe.kt`: ping por socket Unix com timeout curto; `systemctl --user start sfv2.service`.
3. **Janela Compose** — `gradle/libs.versions.toml`, `app/build.gradle.kts`, `ConnectionScreen.kt`, `Main.kt`: janela opaca 640x400 (mínimo 360x240) com os tokens do `DESIGN.md`.

Os testes de aceitação (QA) cobrem `ConnectionMonitor` em `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt`; AC-6 é manual.

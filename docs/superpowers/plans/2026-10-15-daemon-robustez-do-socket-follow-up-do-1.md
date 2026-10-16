# Daemon: robustez do socket (#101) — plano

Spec: `docs/superpowers/specs/2026-10-15-daemon-robustez-do-socket-follow-up-do-1-design.md`. Camada única: `daemon`. Peso `heavy`: o engineer implementa; o QA escreve os testes de aceitação antes.

## Tarefas

### T1 — daemon: log, lock de partida, recuo no accept e `launchDaemon`
- Agente: `engineer` (camada `daemon`). Depende dos testes de aceitação do QA (vermelhos).
- Arquivos:
  - `daemon/src/main/kotlin/sfv2/daemon/DaemonLog.kt` (novo): `fun interface DaemonLog` e `StderrDaemonLog` (`event=<e> key=value`).
  - `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: `start(path, log, pause)`; `daemon.lock` (`0600`) com `FileChannel.tryLock()` antes de `clearOrphan`, liberado em `close()`; `client_error` nos dois `catch` de `serve`; `AcceptLoop` internal com `acceptBackoffMillis(attempt)` (10 ms dobrando até 1000 ms, reinicia após sucesso).
  - `daemon/src/main/kotlin/sfv2/daemon/Main.kt`: `launchDaemon(env, err): DaemonLaunch` (`Started` / `Failed(exitCode = 1)`); `main` fino.

## Ordem
T1 única. Gates depois dela: scope, acceptance-intact, migration, build, green.

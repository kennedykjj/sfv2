# Daemon com socket unix e protocolo — plano

**Issue:** #15 · **Spec:** `docs/superpowers/specs/2026-10-15-daemon-com-socket-unix-e-protocolo-design.md`

## Tarefas

### T1 — layer `daemon` (agente: engineer)

Depende de: nenhuma (única tarefa; a dependência `jackson-databind` em `gradle/libs.versions.toml` é do Tech Lead e entra antes, em commit próprio).

Arquivos (todos sob `daemon/`):

- `daemon/build.gradle.kts`: `implementation(libs.jackson.databind)`.
- `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `parseRequest(line)` e `render(response)`, puras; `Response` selada (`Pong`, `Error`).
- `daemon/src/main/kotlin/sfv2/daemon/SocketPaths.kt`: `resolveSocketPath(env)`; `DaemonStartException` sem `XDG_RUNTIME_DIR`.
- `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: servidor com `ServerSocketChannel` unix, thread virtual por conexão, permissões 0700/0600, detecção de socket órfão, `DaemonAlreadyRunningException`, `close()` remove o socket; linha acima de 64 KiB é erro e fecha a conexão.
- `daemon/src/main/kotlin/sfv2/daemon/Main.kt`: `main`, erros em stderr com exit 1, shutdown hook.

### Testes de aceitação (QA)

- `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` (AC-1 a AC-7).

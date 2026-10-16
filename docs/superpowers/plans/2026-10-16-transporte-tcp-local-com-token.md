# Transporte TCP local com token — plano

**Issue:** #78 · **Spec:** `docs/superpowers/specs/2026-10-16-transporte-tcp-local-com-token-design.md`

Tudo na camada `daemon`. Ordem, cada passo com teste antes (red) e implementação depois (green):

1. **TcpTransport** (`daemon/src/main/kotlin/sfv2/daemon/TcpTransport.kt`, novo): bind em 127.0.0.1 na porta pedida (`0` = livre); token via `OwnerProof.create(<dir do socket>/tcp.token, SecureRandom())`; grava `tcp.port`; `authenticate(client)` lê a primeira linha (máx. 1 KiB, prazo de 5 s por temporizador que fecha o canal), valida `{"cmd":"auth","token":...}`, responde `authenticated` ou erro fixo e fecha. Log só `auth_failed`. Cobre AC-1, AC-2, AC-3, AC-4, AC-5.
2. **SocketServer** (`SocketServer.kt`): parâmetro `tcp: TcpTransport? = null`; segundo `AcceptLoop` com `authenticate` antes de `serve`; `close()` fecha listener e conexões TCP e apaga `tcp.port`. Depende de 1. Cobre AC-6 (comando de dono sem `proof` recusado), AC-7 (sem listener sem a variável) e AC-8.
3. **Partida** (`Main.kt`): lê `SFV2_TCP_PORT`, monta o `TcpTransport`, valor inválido → `DaemonStartException` com a correção. Depende de 2. Cobre AC-7 no nível do `launchDaemon`.
4. Rodar `./gradlew :daemon:test` e `./gradlew build`.

Testes em `SocketServerTest.kt` (AC-1 a AC-6, AC-8) e `ModuleTest.kt` (AC-7); prazo de 5 s testado com prazo curto injetado, sem `Thread.sleep`.

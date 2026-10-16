# Transporte TCP local com token — spec

**Issue:** #78 · **Branch:** `feat/78-transporte-tcp-local-com-token` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** O JDK não tem socket unix com named pipe no Windows; sem outro transporte local, o daemon não atende app, CLI e agentes nessa plataforma (fase 8, S8-01).
**Audience:** O desenvolvedor sozinho com muitos projetos, e o desenvolvedor dentro de uma empresa, quando a máquina é Windows.
**Acceptance criteria**
- **AC-1** — Given o daemon iniciado no Windows (ou com `SFV2_TRANSPORT=tcp`), when ele sobe, then escuta em TCP apenas no loopback, nunca em outra interface.
- **AC-2** — Given o transporte TCP ativo, when um cliente conecta sem token ou com token errado, then a conexão é recusada e nenhum comando é executado.
- **AC-3** — Given o transporte TCP ativo, when um cliente conecta com o token correto, then o daemon serve o mesmo protocolo do socket unix, com as mesmas respostas.
- **AC-4** — Given o daemon que inicia uma sessão em TCP, when o token é gerado, then é novo a cada sessão, fica em arquivo legível só pelo dono e não aparece em log nem em resposta.
- **AC-5** — Given Linux ou macOS sem `SFV2_TRANSPORT=tcp`, when o daemon sobe, then usa o socket unix como hoje, sem mudança de comportamento.
**Out of scope:** cliente CLI e app no Windows; named pipe; TLS; escuta fora do loopback; rotação de token durante a sessão.
**Product fit:** Serve "Daemon local, clientes por socket local" (Operating Context) e a plataforma Windows da fase 8; o token mantém o daemon como único escritor acessível só ao dono da máquina. Não contradiz posicionamento nem escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos.** Protocolo de linhas JSON inalterado. No TCP, a primeira linha da conexão é o handshake `{"cmd":"auth","token":"<token>"}`; resposta `{"ok":true,"result":"authenticated"}`. Token ausente, errado, linha acima do limite ou primeira linha que não é `auth` → `{"ok":false,...}` genérico (sem dizer o que faltou), conexão fechada, nenhum comando despachado. No socket unix não há handshake.

**Camada `daemon`** (única; arquivos em `daemon/src/main/kotlin/sfv2/daemon/`):
- `Transport.kt` (novo): `sealed interface Transport` com `Unix` e `Tcp`; `resolveTransport(env, osName)` pura: `SFV2_TRANSPORT` (`unix`|`tcp`, outro valor → `DaemonStartException`), senão `tcp` se `osName` começa com "Windows", senão `unix`. Também `SessionToken` (32 bytes de `SecureRandom` em hex, comparação com `MessageDigest.isEqual`).
- `SocketPaths.kt`: acrescenta `resolveRuntimeDir(env, osName)`: `XDG_RUNTIME_DIR`; no TCP, cai em `LOCALAPPDATA` quando o primeiro falta. O caminho do socket unix segue exigindo `XDG_RUNTIME_DIR`.
- `SocketServer.kt`: `start` recebe `transport`. No TCP: `ServerSocketChannel.open()` com bind em `InetAddress.getLoopbackAddress()`, porta `SFV2_TCP_PORT` ou 0 (efêmera); grava `daemon.port` e `session.token` na pasta de runtime (o `session.token` é criado já com `rw-------` numa só operação, como o `OwnerProof` faz com o `owner.token`, nunca criado e só depois restringido, para não haver janela em que outro usuário o leia; onde o sistema de arquivos não suporta POSIX, cria o arquivo na pasta do perfil do usuário sem chamar `setPosixFilePermissions`); `serve` exige o handshake antes do laço de comandos, dentro de um prazo de 5 s para a primeira linha (conexão muda é fechada e conta como `auth_refused`, para não prender uma thread); `close` apaga os dois arquivos. O lock de instância única continua valendo nos dois transportes. `AcceptLoop` já aceita `SocketChannel`, sem mudança.
- `Main.kt`: `launchDaemon` resolve o transporte e passa ao `SocketServer`; `owner.token` (prova do dono) segue separado do token de sessão.
- Log: eventos `auth_refused` e `listening` só com `transport` e `port`; nunca o token.

**Migração:** nenhuma (sem esquema nem dado persistido novo; os arquivos de runtime são recriados a cada start).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Handshake `auth` na primeira linha, em vez de token em cada requisição: o protocolo e todos os comandos existentes ficam intactos e a recusa acontece antes de qualquer despacho.
- Seleção por `SFV2_TRANSPORT` com padrão por sistema operacional: permite testar o TCP no Linux sem uma máquina Windows; `osName` entra por parâmetro para testar a escolha.
- Porta efêmera publicada em `daemon.port`: evita conflito de porta fixa; `SFV2_TCP_PORT` fica como override.
- Token de sessão separado do `owner.token`: um autentica a conexão, o outro prova o dono nos comandos; juntar os dois mudaria o modelo de ator.
- Sem arquivo de teste novo: `SocketServerTest.kt` já cobre o servidor e o transporte.
- Cliente CLI/app no TCP fica fora (já declarado no PO); a CLI hoje só lê o socket unix, então no Windows só passa a funcionar quando um card próprio ensinar o cliente.
- Permissão do arquivo do token no Windows depende da pasta do perfil do usuário (ACL herdada); não verificável em Linux, fica para a VM da saída da fase 8.

## Open questions
- none

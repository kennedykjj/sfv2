# Runtime codex: servidor MCP por URL com token fora da linha de comando — spec

**Issue:** #135 · **Branch:** `feat/135-runtime-codex-servidor-mcp-por-url` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o runtime codex só traduz servidores MCP por `command`/`args`; o servidor MCP do daemon é HTTP em loopback com token de sessão, então o codex não consegue falar com o daemon e o lançamento falha. Fechar a lacuna sem expor o token em `/proc/<pid>/cmdline` nem em mensagens de erro.

**Audiência:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa (ambos rodam agentes codex pela fábrica na própria máquina).

**Critérios de aceitação**
- AC-1: Dado um `mcpConfig` com servidor por URL e token, quando o runtime codex lança a sessão, então aplica `mcp_servers.<nome>.url` e entrega o token por variável de ambiente do processo (`bearer_token_env_var`).
- AC-2: Dado um servidor por URL e token, quando a sessão é lançada, então nenhum argumento da linha de comando contém o token (verificado nos argumentos do `ProcessRunner` falso).
- AC-3: Dado um `mcpConfig` ilegível, quando o runtime tenta lê-lo, então o erro cita o caminho do arquivo e não repete seu conteúdo.
- AC-4: Dado um `mcpConfig` inválido, quando o runtime o decodifica, então o erro cita o caminho e o campo problemático, sem conteúdo do arquivo nem a mensagem da biblioteca de JSON.
- AC-5: Dado um servidor por `command`/`args`, quando a sessão é lançada, então o comportamento é idêntico ao do #23.

**Fora do escopo:** suporte a Bedrock/Vertex; o servidor MCP em si (#28); outros runtimes; mudança no formato de token ou na sua geração.

**Adequação ao produto:** serve ao princípio de que texto e segredos não vazam (segredo nunca em linha de comando ou log) e ao modelo "agentes falam com o daemon por MCP". Não contradiz o posicionamento nem o fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Camada:** só `adapters`. Sem mudança em `ports` (`SessionRequest` fica igual) nem migração de dados.
- **Contratos:**
  - `ProcessRunner.run(command, workingDir, stdoutFile, timeout, env: Map<String, String> = emptyMap())`: `env` é somado ao ambiente herdado do processo filho. O padrão vazio mantém os chamadores atuais.
  - `McpServerEntry` aceita `command`/`args` ou `url` + `headers` (`Map<String, String>`). Entrada com `url` e `headers.Authorization = "Bearer <token>"`.
  - Para servidor por URL, o runtime emite `-c mcp_servers.<nome>.url="<url>"` e `-c mcp_servers.<nome>.bearer_token_env_var="SFV2_MCP_TOKEN_<NOME>"`; o token vai em `env` do `ProcessRunner`, nunca em `-c`. O nome da variável deriva do nome do servidor em maiúsculas, com `-` trocado por `_`.
  - Leitura do `mcpConfig`: o erro cita só o caminho e o campo (ex.: `mcpServers.<nome>.url`), sem `e.message` e sem conteúdo do arquivo. Campo derivado do caminho da exceção do `kotlinx.serialization`, ou "document" se não houver.
- **Arquivos:**
  - `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt` (entrada por URL, env, erros sem conteúdo)
  - `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt` (aplica `env` ao `ProcessBuilder`)
  - `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` (fake registra `env`; novos casos)
- **Migração:** n/a.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt` |

## Security
_Cybersec_

**Dados sensíveis:** token de sessão do MCP (bearer, loopback) e o `mcpConfig`, que o contém em `headers.Authorization`.

**Controles exigidos**
- O token só vai em `env` do `ProcessRunner`; nenhum `-c` contém o token nem o valor de `Authorization`, só `url` e o NOME da variável.
- O `env` do filho recebe só as variáveis `SFV2_MCP_TOKEN_*` dos servidores da sessão. O filho herda o token (aceito: token de sessão, só loopback, `/proc/<pid>/environ` só do mesmo usuário).
- O nome do servidor é validado contra `[A-Za-z0-9_-]+` antes de montar a chave `-c` e o nome da variável (sem injeção de chave TOML). Dois servidores que geram a mesma variável (`a-b` e `a_b`) falham com erro que cita só os nomes.
- O valor de `url` no `-c` tem aspas e barras escapadas. Com token, só `http(s)` e host loopback (`127.0.0.1`, `localhost`, `::1`); caso contrário o lançamento falha (impede enviar o bearer a host remoto por `mcpConfig` adulterado).
- Os erros de leitura/decodificação do `mcpConfig` (hoje com `${e.message}` em `CodexRuntime.kt:74` e `:76`) citam só caminho e campo; sem `e.message` e sem a exceção original como `cause`.
- Não logar `env`, valores de `-c` nem o `mcpConfig`; `ProcessRunnerImpl` não imprime `env` em exceção de timeout ou de falha ao iniciar.
- Verificação no review (fora do escopo): o daemon grava o `mcpConfig` com modo 0600 e o remove no fim da sessão.

**Testes de segurança:** token sentinela no arquivo não aparece na `message` dos erros; nenhum elemento de `command` contém o token e `env` o contém; colisão de variável, nome inválido e `url` não loopback com token falham; servidor `command`/`args` não recebe `env` extra.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Token no `env` via novo parâmetro `env` (padrão vazio) em `ProcessRunner`: o contrato atual não passa ambiente e `bearer_token_env_var` exige variável no processo.
- Nome da variável `SFV2_MCP_TOKEN_<NOME>` (maiúsculas, `-` vira `_`): evita colisão com variáveis do codex e permite vários servidores.
- Entram no escopo, por segurança: validação de colisão/nome, `url` com token só em loopback e remoção de `e.message`; são pequenos e fecham o vazamento do token. ACs 3 e 4 já cobrem os erros; a restrição a loopback e a colisão são cobertas nos mesmos testes de `CodexRuntimeTest`.
- Restringir `url` com token a loopback agora: o servidor MCP do daemon (#28) só escuta em loopback, e o `mcpConfig` adulterado não deve levar o token para fora da máquina.
- Peso `heavy`: a spec tem seção `## Security` preenchida.
- Nenhum arquivo novo de teste: `CodexRuntimeTest.kt` já cobre o módulo.

## Open questions
- none

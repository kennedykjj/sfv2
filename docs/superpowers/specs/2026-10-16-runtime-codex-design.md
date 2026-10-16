# Runtime codex — spec

**Issue:** #23 · **Branch:** `feat/23-runtime-codex` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** a fábrica só tem um runtime de agente; sem um segundo, fica amarrada a um provedor. Falta um adaptador que rode sessões com o codex.
**Audience:** o desenvolvedor sozinho com muitos projetos e o desenvolvedor dentro de uma empresa, que escolhem o provedor do agente por projeto ou política.
**Acceptance criteria**
- **AC-1** — Dado uma sessão com worktree e configuração MCP do daemon, quando o runtime codex a lança, então roda `codex exec` com o diretório de trabalho no worktree e com a configuração MCP do daemon aplicada.
- **AC-2** — Dado o fim normal da sessão, quando o adaptador conclui, então devolve o caminho da transcrição e o código de saída do processo.
- **AC-3** — Dado que o `codex exec` termina com código diferente de zero, quando a sessão acaba, então o adaptador devolve esse código e o caminho da transcrição, sem tratá-lo como sucesso.
- **AC-4** — Dado o runtime codex, quando usado no lugar do runtime existente, então atende a mesma port de runtime e o chamador não precisa mudar.
- **AC-5** — Dado um `mcpConfig` com nome de servidor fora de `[A-Za-z0-9_-]+` ou um `stage` fora de `[a-z][a-z0-9-]*`, quando a sessão é lançada, então o adaptador recusa com erro que nomeia o campo, nenhum processo inicia e nenhum arquivo é criado; valores de `command` e `args` com aspas ou barra são codificados como texto TOML e nunca alteram a chave do override.
**Out of scope:** provedor próprio (runtime.provider da V0); seleção de runtime por projeto na UI; cálculo de custo a partir da transcrição.
**Product fit:** serve o princípio de a fábrica ser dona do próprio estado e não depender de um provedor; a transcrição devolvida alimenta o custo medido por run. Não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contrato:** nenhum novo na port. `class CodexRuntime(processRunner: ProcessRunner, transcriptDir: Path, clock: Clock, timeout: Duration) : Runtime` em `adapters`; `launch(SessionRequest): SessionResult` como em `ports/.../Runtime.kt`.
- **`ProcessRunner`** (interface mínima no próprio `adapters`): `run(command: List<String>, workingDir: Path, stdoutFile: Path, timeout: Duration): ProcessOutcome` com `ProcessOutcome` = `Exited(code)` | `TimedOut`. A implementação real usa `ProcessBuilder` com timeout e mata o processo ao estourar; os testes usam um fake gravador que devolve o desfecho roteirizado.
- **Comando:** `codex exec --json --cd <worktree> --model <model> -c mcp_servers.<nome>.command=... -c mcp_servers.<nome>.args=[...]` (uma entrada por servidor do `mcpConfig`). O prompt do estágio não faz parte desta issue: `request.stage` só nomeia a transcrição.
- **MCP:** `mcpConfig` é o JSON do daemon (`{"mcpServers": {nome: {command, args}}}`); o adaptador o lê e o traduz em overrides `-c` do codex, sem tocar em `~/.codex`.
- **Transcrição:** stdout `--json` redirecionado a `<transcriptDir>/<stage>-<epochMillis do clock>.jsonl`; o caminho vai em `SessionResult`. Código ≠ 0 → `Exited(code, path)`; estouro de timeout → `Hung(path)`.
- **Arquivos (3):** `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt` (runtime + `ProcessRunner` + montagem do comando), `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt` (implementação com `ProcessBuilder`), `adapters/src/test/kotlin/sfv2/adapters/CodexRuntimeTest.kt`. Camada dona: `adapters`. Dependência nova: nenhuma (JSON do `mcpConfig` lido com o que o módulo já tiver; se faltar parser, adicionar o kotlinx-serialization-json em `gradle/libs.versions.toml` com versão fixa).
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
_Revisão do dono interino_
- **O nome do servidor MCP vira chave de configuração** (`-c mcp_servers.<nome>...`) e os valores vão como texto TOML: validar o nome e escapar os valores (AC-5); sem shell em nenhum passo.
- **Processo externo com timeout** (já no desenho) e kill da árvore ao estourar; a saída `--json` pode conter segredos: só o caminho vai para evento e log.
- **Nenhum acesso a `~/.codex`:** a sessão não altera a configuração pessoal do dono (já decidido).

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo (`CodexRuntimeTest.kt`): nenhum teste cobre runtimes em `adapters` ainda (só `ModuleTest`).
- MCP aplicado por overrides `-c` e não por `~/.codex/config.toml`: a sessão não pode alterar a configuração pessoal do dono.
- Testes sem codex real: `ProcessRunner` fake registra o comando e devolve o desfecho; AC-4 é verificado atribuindo `CodexRuntime` a uma variável `Runtime` e lançando como o chamador faria.
- Timeout vira `Hung`, como a port já prevê para o watchdog; o valor vem do construtor, sem relógio real nos testes.
- Prompt do estágio fica fora: pertence às skills dos estágios (fase 2 posterior), a issue pede só o lançamento.
- Peso `standard`: uma camada, 4 AC, 3 arquivos, sem UI nem segurança.

## Open questions
- none

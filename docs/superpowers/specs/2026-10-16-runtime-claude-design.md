# Runtime claude — spec

**Issue:** #22 · **Branch:** `feat/22-runtime-claude` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** A fábrica ainda não executa nenhum agente de verdade: falta o primeiro runtime real, que lance o `claude` em modo headless numa sessão, ligado ao daemon por MCP, e devolva o que o daemon precisa para medir custo e decidir (transcrição e código de saída).
**Audience:** O desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de empresa), que só vê a fábrica trabalhar quando o agente realmente roda.
**Acceptance criteria**
- **AC-1** — Given uma sessão com worktree definido, when ela é lançada, then o `claude` roda em modo headless (`-p`) com esse worktree como diretório de trabalho.
- **AC-2** — Given uma sessão lançada, when o processo inicia, then ele recebe uma configuração MCP que aponta para o daemon.
- **AC-3** — Given uma sessão que termina, when o adaptador conclui, then devolve o caminho da transcrição da sessão.
- **AC-4** — Given uma sessão que termina, when o adaptador conclui, then devolve o código de saída do processo, inclusive quando diferente de zero.
- **AC-5** — Given que o binário `claude` não existe no sistema, when a sessão é lançada, then o erro diz que o binário está ausente (com o id da sessão), sem stack trace.
- **AC-6** — Given o runtime do adaptador, when usado pelo restante da fábrica, then cumpre o contrato da port `Runtime` (mesmos testes de contrato do fake).
**Out of scope:** Bedrock e Vertex (fase 9); outros runtimes; cálculo de custo a partir da transcrição; decidir o que fazer com o código de saída (é dos gates e do daemon).
**Product fit:** Serve "custo medido das transcrições de cada run" (a transcrição volta ao daemon) e "gates, não a palavra do agente" (o adaptador devolve fatos: transcrição e código de saída, sem interpretar). Sem conflito com posicionamento nem com o fora de escopo. Roadmap fase 2, S2-04.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:** a port `Runtime`/`SessionRequest`/`SessionResult` não muda. Novo no `adapters`: `class ClaudeRuntime(binary: String = "claude", transcriptDir: Path) : Runtime` e `class RuntimeBinaryNotFoundException(binary: String, stage: String) : RuntimeException`.

**Camadas e módulos:** só `adapters` (depende de `ports`, já declarado).

**Arquivos:**
- `adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt` (novo; a exceção fica no mesmo arquivo)
- `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` (novo)

**Comportamento de `launch`:**
- Monta `claude -p --permission-mode acceptEdits --strict-mcp-config --mcp-config <mcpConfig> --model <model> --output-format stream-json --verbose`, com `ProcessBuilder.directory(worktree)`; o prompt `/sf:<stage>` vai pelo stdin (como na V0, `claude_code.py`).
- O stdout (stream-json) é gravado em `transcriptDir/<stage>-<instante>.jsonl`; esse arquivo é o `transcriptPath`. O stderr vai para o mesmo arquivo (`redirectErrorStream`) para não travar o pipe.
- Espera o fim do processo e devolve `SessionResult.Exited(exitCode, transcriptPath)`, com o código bruto (sem interpretar).
- Binário ausente (`IOException` ao iniciar): lança `RuntimeBinaryNotFoundException` com mensagem `claude binary not found: <binary> (stage=<stage>)`, sem `cause` exposto; o chamador imprime só a mensagem.
- `Hung` não é produzido aqui: detectar travamento é do watchdog (fora do escopo).
- O nome do arquivo usa `Clock` injetado (`clock: Clock = Clock.systemUTC()`) por parâmetro de construtor.

**Migração:** n/a (sem dados nem contrato público alterado).

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

Os testes rodam o `ClaudeRuntime` em processo contra um executável falso (script `sh` em `@TempDir`) que grava o diretório de trabalho e os argumentos recebidos e sai com o código pedido.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-6 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada, mas 6 critérios (mais de 3 exclui `light`; não passa de 6).
- Arquivo de teste novo: o módulo `adapters` só tem o `ModuleTest` de placeholder; nenhuma suíte cobre runtimes.
- A transcrição é o stdout do `claude` gravado pelo adaptador em `transcriptDir`, e não o jsonl de `~/.claude`: independe do layout interno do claude e é testável sem ele.
- Binário ausente vira exceção de domínio com mensagem limpa, sem mudar a port: `SessionResult` não tem caso de erro de lançamento, e alterar a port é outro card.
- O prompt é `/sf:<stage>` pelo stdin: `SessionRequest` não carrega o número do card; ampliar a request fica para o card que ligar o dispatcher.
- Sem `--plugin-dir`, `--add-dir` nem `--setting-sources` por ora: dependem de configuração do projeto que a `SessionRequest` ainda não traz.
- AC-6 foi acrescentado pelo PO; é coberto afirmando que `ClaudeRuntime` usado como `Runtime` devolve `Exited`.

## Open questions
- none

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
- **AC-7** — Given uma sessão que não termina dentro do tempo limite (`timeout` do construtor), when o limite estoura, then o adaptador mata o processo e seus descendentes e devolve `SessionResult.Hung(transcriptPath)`, sem esperar para sempre e sem sleep nem relógio real nos testes.
- **AC-8** — Given um `stage` fora de `[a-z][a-z0-9-]*` ou um worktree que não existe, when a sessão é lançada, then o adaptador recusa com erro que nomeia o campo (`stage` ou `worktree`), nenhum processo é iniciado e nenhum arquivo é criado.
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
- Espera o fim do processo com **timeout** (`timeout: Duration` no construtor, sem valor mágico escondido) e devolve `SessionResult.Exited(exitCode, transcriptPath)`, com o código bruto (sem interpretar). Ao estourar o tempo, mata o processo e os descendentes e devolve `SessionResult.Hung(transcriptPath)` (AC-7): `claude -p` que imprime o resultado e não encerra já prendeu a V0 por até 3 h. O watchdog do daemon continua dono da política (quando disparar); o adaptador só garante que nunca bloqueia sem limite (ARCHITECTURE: todo processo externo com timeout).
- `stage` e `worktree` são validados antes de iniciar (AC-8); `worktree` ausente não é confundido com binário ausente.
- Binário ausente (`IOException` ao iniciar, com o worktree válido): lança `RuntimeBinaryNotFoundException` com mensagem `claude binary not found: <binary> (stage=<stage>)`, sem `cause` exposto; o chamador imprime só a mensagem.
- `Hung` só é produzido pelo timeout do adaptador (AC-7); a política de watchdog (quando e quanto) continua do daemon.
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
| AC-7 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-8 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |

## Security
_Revisão do dono interino_
- **Processo externo sempre com timeout** (AC-7) e sem shell; o prompt `/sf:<stage>` é montado só de `stage` validado (AC-8), nunca de texto de issue, spec ou transcrição.
- **`--permission-mode acceptEdits` não dispensa a allowlist do projeto** (`.claude/settings.json`); o adaptador não acrescenta permissões.
- **A transcrição pode conter segredos e texto de issue:** fica em `transcriptDir`, nunca em log nem em evento (só o caminho).

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

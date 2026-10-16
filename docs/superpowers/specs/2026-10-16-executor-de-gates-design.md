# Executor de gates — spec

**Issue:** #26 · **Branch:** `feat/26-executor-de-gates` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Gates são scripts, mas o que move um card é o veredito deles registrado no estado da fábrica. Falta o executor que roda um gate e transforma o resultado em evento, sem deixar um gate travado prender o card nem encher o banco com saída longa.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, na empresa, quem precisa de trilha de auditoria): confia no veredito do gate, não no relato do agente.
**Acceptance criteria**
- **AC-1** — Given um gate executado pelo executor, when ele termina, then o resultado é registrado como evento com ator `factory:gate`, o veredito (pass ou fail) e a saída resumida.
- **AC-2** — Given um gate que excede o tempo limite, when o limite estoura, then o veredito é fail com motivo `timeout`.
- **AC-3** — Given um gate com saída longa, when ele termina, then a saída completa fica em arquivo do run, fora do banco, e o evento guarda só o resumo.
**Out of scope:** Os gates em si (o conteúdo e as regras de cada script); decidir o que o veredito faz com o card.
**Product fit:** Serve ao princípio "gates são scripts, não a palavra do agente" e a "toda ação é um evento com ator". Não contradiz o posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core`: novo `EventType.GATE_FINISHED` em `core/src/main/kotlin/sfv2/core/Event.kt`. Payload (`Map<String, String>`): `gate`, `verdict` (`PASS` | `FAIL`), `reason` (`exit_code` | `timeout` | `start_error`), `exit_code` (ausente em timeout/start_error), `summary` (últimas 20 linhas, máx. 2000 caracteres), `output_file` (caminho do arquivo do run, nunca o conteúdo).
- `daemon`: `GateExecutor(store: CardEventStore, events: EventStream, clock: Clock, runner: GateRunner)` com `execute(cardId: String, spec: GateSpec, runDir: Path): GateVerdict`. `GateSpec(name, command: List<String>, workingDir: Path, timeout: Duration)`. `GateVerdict` é sealed (`Pass`, `Fail(reason)`), não booleano.
- `daemon`: interface `GateRunner.run(spec, outputFile): GateRun` (`exitCode?`, `timedOut`) e `ProcessGateRunner` (ProcessBuilder, stdout+stderr mesclados direto para o arquivo, `destroyForcibly` na árvore ao estourar o tempo).

**Camadas e arquivos**
- `core`: `core/src/main/kotlin/sfv2/core/Event.kt`.
- `daemon`: `GateExecutor.kt`, `ProcessGateRunner.kt` (novos, em `daemon/src/main/kotlin/sfv2/daemon/`).
- Testes: `core/src/test/kotlin/sfv2/core/EventTest.kt`, `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt`.

**Fluxo:** o executor cria `runDir`, roda o gate com a saída indo para `runDir/gate-<name>.log`, calcula o resumo lendo só o fim do arquivo, monta o `Event` (ator `Actor.Factory("gate")`, `at` do `Clock`), grava com `store.recordChange(cardId, card, event)` (card inalterado, mesma transação do evento) e publica no `EventStream`. Card inexistente: `GateCardNotFoundException` (RuntimeException, com o id), nada gravado. Log estruturado `key=value` com o id do card e o nome do gate, nunca a saída.

**Migração:** nenhuma; `EventType` é só nome gravado, sem esquema novo (expand-only).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca duas camadas (`core` para o tipo de evento, `daemon`); a nota da issue esperava standard.
- Novo tipo `GATE_FINISHED` em vez de reaproveitar outro: o catálogo de eventos é fechado e o resultado de gate é um fato distinto.
- Evento fica preso a um card (a tabela de eventos exige `card_id`); gates sem card ficam para quem os chamar.
- Arquivo de saída em `<runDir>/gate-<name>.log`; o evento guarda o caminho, não o conteúdo. O diretório do run vem do chamador (dispatcher, #27).
- Resumo = últimas 20 linhas, máx. 2000 caracteres: o fim da saída é onde o gate diz por que falhou.
- Teste de timeout usa o `GateRunner` falso; só o teste do `ProcessGateRunner` usa processo real, com timeout curto e sem `Thread.sleep`.
- Arquivo de teste novo `GateExecutorTest.kt`: nenhuma suíte do daemon cobre gates.
- Falha ao iniciar o processo vira `FAIL` com motivo `start_error`, não exceção: o gate falhou, o executor não.

## Open questions
- none

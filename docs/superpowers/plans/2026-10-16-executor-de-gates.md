# Executor de gates — plano (#26)

Spec: `docs/superpowers/specs/2026-10-16-executor-de-gates-design.md`

## Tarefas

### T1 — core: `EventType.GATE_FINISHED`
- Camada: `core` · Agente: `engineer` · Depende de: nada
- Arquivos: `core/src/main/kotlin/sfv2/core/Event.kt` (adicionar `GATE_FINISHED` ao enum).

### T2 — daemon: `GateExecutor`, `GateRunner`, `ProcessGateRunner`
- Camada: `daemon` · Agente: `engineer` · Depende de: T1
- Arquivos (novos): `daemon/src/main/kotlin/sfv2/daemon/GateExecutor.kt`, `daemon/src/main/kotlin/sfv2/daemon/ProcessGateRunner.kt`.
- `GateSpec`, `GateVerdict` (sealed `Pass`/`Fail(reason)`), `GateRun`, `GateRunner`, `GateCardNotFoundException`.
- Valida `name` (`[a-z0-9][a-z0-9-]*`) antes de qualquer IO; arquivo de saída `runDir/gate-<name>.log` dentro de `runDir`; resumo = últimas 20 linhas, máx. 2000 caracteres, lido do fim do arquivo; evento `GATE_FINISHED` com ator `Factory("gate")` gravado via `store.recordChange` e publicado no `EventStream`; log `key=value` sem a saída.
- `ProcessGateRunner`: sem shell, saída mesclada para o arquivo, `destroyForcibly` na árvore ao estourar o tempo.

## Testes
Escritos pela QA antes da implementação: `core/src/test/kotlin/sfv2/core/EventTest.kt`, `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt`.

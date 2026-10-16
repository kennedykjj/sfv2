# Custo por transcrição — plano (#33)

Spec: `docs/superpowers/specs/2026-10-16-custo-por-transcricao-design.md`

## Tarefas

1. **core** (engineer) — `core/src/main/kotlin/sfv2/core/Event.kt`: adicionar `RUN_COST_RECORDED` a `EventType`. Sem dependências.
2. **daemon** (engineer) — depende de 1. Arquivos novos em `daemon/src/main/kotlin/sfv2/daemon/`:
   - `PriceTable.kt` (`parse(yaml)`, `priceOf(model)`, `ModelPrice` em `BigDecimal` por milhão de tokens).
   - `TranscriptParsers.kt` (`parse(runtime, transcript, fallbackModel): Usage`, claude e codex, streaming linha a linha, linha > 1 MiB ignorada, arquivo > 64 MiB desconhecido com `reason`).
   - `RunCostCalculator.kt` (`RunCost` sealed: `Known`, `Partial`, `Unknown`).
   - `RunCostRecorder.kt` (parseia, calcula, grava pelo `EventRecorder`, nunca lança, ator `Actor.Factory("cost")`).
   - Fixtures mínimas em `daemon/src/test/resources/transcripts/` ficam com a QA.

## Ordem
core → daemon, uma tarefa por vez no mesmo worktree.

# Interface Tracker e fake — plano

**Issue:** #52 · **Spec:** `docs/superpowers/specs/2026-10-16-interface-tracker-e-fake-design.md`

Camada única: `ports`. Sem dependências entre tarefas além da ordem abaixo.

## Tarefas

### T1 — layer `ports`, agente `engineer`: contrato `Tracker`
- Arquivo: `ports/src/main/kotlin/sfv2/ports/Tracker.kt`
- `Tracker` (`fetch`, `search`, `writeMilestone`, `close`), `CloseResult` (`Closed`/`AlreadyClosed`), `TrackerItem` com fábrica pura `TrackerItem.of(...)` (SHA-256 hex de `title + NUL + body`; validação de url `https://`, ≤ 2048, sem caractere de controle), `TrackerItemState`, `ExternalText` (`toString()` não revela o valor), `TrackerException`, `UnknownTrackerItemException`.

### T2 — layer `ports`, agente `engineer`: `FakeTracker` (depende de T1)
- Arquivo: `ports/src/main/kotlin/sfv2/ports/FakeTracker.kt`
- Em memória, sem IO/relógio; `seed`, `failNext(operation)`, `calls`, `milestones`, `closures`; fechar item já fechado devolve `AlreadyClosed` sem novo comentário. Segue o estilo do `FakeForge`.

Testes de aceitação (`TrackerTest.kt`) vêm do QA, antes das tarefas.

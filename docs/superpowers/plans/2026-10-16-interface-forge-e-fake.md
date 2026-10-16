# Interface Forge e fake — plano (#43)

**Spec:** `docs/superpowers/specs/2026-10-16-interface-forge-e-fake-design.md` · **Peso:** light

## Tarefas

### T1 — contratos e fake do Forge (camada `ports`)
- **Dono:** `engineer` (camada `ports`)
- **Dependências:** nenhuma
- **Arquivos:**
  - `ports/src/main/kotlin/sfv2/ports/Forge.kt` (novo): `interface Forge`, `PullRequestRequest`, `PullRequestRef`, `ChecksState` (+ `FailedCheck`), `MergeResult`, `PullRequestState`.
  - `ports/src/main/kotlin/sfv2/ports/FakeForge.kt` (novo): `class FakeForge` com roteiro (`scriptChecks`, `mergedOutside`, `closeOutside`, `pushHead`, `scriptConflict`, `failNext`), `opened`, `merges`; `class ForgeException` e `UnknownPullRequestException`.
  - `ports/src/test/kotlin/sfv2/ports/ForgeTest.kt` (novo): suite dos acceptance tests AC-1 a AC-9 (escrito pela QA).

**Ordem dentro da tarefa:** contratos e exceções antes do fake; o fake segue o padrão de `FakeRuntime.kt`.

**Regras de execução:** `summary` de `FailedCheck` truncado em 2048 bytes UTF-8; fake sem IO, relógio ou rede; `merge` não altera estado quando recusa; `failNext` não grava nada pela metade.

## Fora deste plano
- Nenhuma outra camada, migração ou UI.

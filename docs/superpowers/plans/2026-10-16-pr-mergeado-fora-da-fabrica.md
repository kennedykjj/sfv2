# PR mergeado fora da fábrica — plano

**Issue:** #48 · **Spec:** `docs/superpowers/specs/2026-10-16-pr-mergeado-fora-da-fabrica-design.md`

Ordem: ports → adapters → daemon (o daemon depende da porta; o adapter implementa a porta).

## Tarefa 1 — ports (engineer)
- `ports/src/main/kotlin/sfv2/ports/Forge.kt`: `fun findMergedPullRequest(branch: String): MergedPullRequest?` e `data class MergedPullRequest(number, mergeCommit, head, mergedBy: String?)`.
- `ports/src/main/kotlin/sfv2/ports/FakeForge.kt`: acha entrada `MERGED` pela branch, devolve `merge-<n>`, respeita `failNext("findMergedPullRequest")`.
- Teste de contrato em `ForgeTest` (camada ports).

## Tarefa 2 — adapters (engineer, depende da 1)
- `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt`: `gh pr list --head <branch> --state merged --limit 1 --json number,mergeCommit,headRefOid,mergedBy`; mesma validação de repo/branch do `findOpenPullRequest`; `mergedBy` só se casar `^[A-Za-z0-9-]{1,39}$`; `mergeCommit.oid` ausente → `ForgeException`.
- Cobertura em `GhForgeTest` (AC-9).

## Tarefa 3 — daemon (engineer, depende da 1)
- `daemon/src/main/kotlin/sfv2/daemon/AutonomousDelivery.kt`: nos dois pontos de `pr_not_open` (nenhum PR aberto; `MergeResult.NotOpen`/Merged) consultar `findMergedPullRequest`. Head igual ao aprovado: `PR_MERGED` com `origin=external` (+ `merged_by` se houver), mover a `DONE` com `merge_commit`, `origin=external`, `reason=merged_outside_factory`, logar `delivery_done origin=external`, `DeliveryOutcome.Done`, sem CI da main. Head diferente: Staged com `merged_head_differs expected=<sha> actual=<sha>` e `PR_MERGED` externo gravado. Não achou: `pr_not_open`. `ForgeException`: `forge_failed`.
- `daemon/src/main/kotlin/sfv2/daemon/ProjectDelivery.kt`: `UnavailableForge` implementa o método lançando a exceção dos demais.
- Merge feito pela fábrica segue sem `origin` (AC-4).

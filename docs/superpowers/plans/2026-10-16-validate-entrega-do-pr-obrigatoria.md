# Validate: entrega do PR obrigatória — plano

**Issue:** #174 · **Spec:** `docs/superpowers/specs/2026-10-16-validate-entrega-do-pr-obrigatoria-design.md`

## Tarefas

### daemon

1. **Tornar `delivery` obrigatório** (dono: Tech Lead, solo — uma camada, peso standard)
   - `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt`: `delivery: PullRequestDelivery` logo após `model`, sem padrão; `deliver()` chama `delivery.deliver(...)` direto; `head = { delivery.headOf(worktree.path) }`.
   - Chamadores de teste existentes (`ValidatePipelineTest`, `DevelopPipelineTest`) passam uma entrega sobre `FakeForge` com CI verde roteirizado.

### e2e

2. **Ajuste de construção** em `e2e/src/test/kotlin/sfv2/e2e/PhaseExitTest.kt` (área de QA, só construção).

## Dependências

QA escreve primeiro os testes de aceitação (`ValidatePipelineTest`), depois a tarefa 1.

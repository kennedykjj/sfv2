# Marcos opcionais e aviso de texto alterado — plano

**Issue:** #55 · **Spec:** `docs/superpowers/specs/2026-10-16-marcos-opcionais-e-aviso-de-texto-altera-design.md`

## Tarefas

### T1 — core (agente: engineer)
Sem dependências.
- `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt`: `enum class Milestone(val key: String)` (`STARTED`, `PR_OPENED`), campo `milestones: Set<Milestone>` (padrão vazio), leitura da chave `milestones` em `from(raw)`; valor que não é lista ou chave desconhecida lança `InvalidPolicyException("milestones", ...)`.
- `core/src/main/kotlin/sfv2/core/Event.kt`: `EventType.MILESTONE_WRITTEN` e `EventType.EXTERNAL_TEXT_CHANGED`.

### T2 — daemon (agente: engineer; depende de T1)
- `daemon/src/main/kotlin/sfv2/daemon/ExternalMilestones.kt` (novo): `reached(cardId, milestone, detail)` conforme a spec (`@Synchronized`, nunca lança, uma vez por marco via `MILESTONE_WRITTEN`, aviso de texto alterado uma vez por `newHash`, texto do marco `started` / link do PR).
- `daemon/src/main/kotlin/sfv2/daemon/CardImport.kt`: registrar `EXTERNAL_TEXT_CHANGED` na reimportação com hash diferente, com a lógica compartilhada com `ExternalMilestones`.
- `daemon/src/main/kotlin/sfv2/daemon/DevelopPipeline.kt`: `reached(cardId, STARTED)` após Ready for Dev → Coding.
- `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt`: `reached(cardId, PR_OPENED, "<número>")` quando `deliver` devolve `CiResult.Green`.
- `daemon/src/main/kotlin/sfv2/daemon/Main.kt`: montar `ExternalMilestones`.

## Testes (QA)
`ProjectPoliciesTest.kt` (core), `ExternalMilestonesTest.kt` (novo) e `CardImportTest.kt` (daemon), conforme o test plan da spec.

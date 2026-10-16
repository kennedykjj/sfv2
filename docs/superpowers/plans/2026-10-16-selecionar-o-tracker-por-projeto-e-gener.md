# Selecionar o tracker por projeto e generalizar o provider (Jira) — plano

**Issue:** #197 · **Spec:** `docs/superpowers/specs/2026-10-16-selecionar-o-tracker-por-projeto-e-gener-design.md`

## Tarefas

### T1 — core (agente: engineer, depende de: nenhuma)
- Arquivo: `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt`.
- `TrackerConfig` sealed (`GitHub`, `Jira(site, project)`) com `provider`; `ProjectPolicies.tracker`; `from()` lê `tracker`, `jira_site`, `jira_project`, valida valor e formato (`InvalidPolicyException(field)`); `DEFAULT` com `GitHub`.

### T2 — daemon (agente: engineer, depende de: T1)
- Arquivos: `ProjectRegistry.kt` (POLICY_KEYS), `ProjectDelivery.kt` (`trackerOf` com `AcliTracker`, `pullRequestUrl`), `CardImport.kt`, `CardClosure.kt`, `ExternalMilestones.kt` (provider da política, `prLinkOf`), `Main.kt`, `RefineRuntime.kt` (fiação), `TextChangeWarning` com provider por parâmetro.
- Fechamento sem link de PR recusa nomeando o card; marco `pr_opened` não é escrito sem link.

## Notas
- `adapters` não muda. Testes de aceitação: QA, nos arquivos do test plan da spec.

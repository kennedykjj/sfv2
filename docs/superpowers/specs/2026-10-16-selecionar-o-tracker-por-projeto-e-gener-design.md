# Selecionar o tracker por projeto e generalizar o provider (Jira) — spec

**Issue:** #197 · **Branch:** `feat/197-selecionar-o-tracker-por-projeto-e-gener` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o `AcliTracker` (#85) existe, mas nada o seleciona: importação, fechamento e marcos têm `github` fixo e o link do PR depende do sufixo `/issues/<id>` da url, que no Jira é `/browse/PROJ-1`. O dono que trabalha numa empresa com Jira não consegue importar nem fechar tickets.

**Público:** o desenvolvedor dentro de uma empresa (Jira, trilha de auditoria).

**Critérios de aceitação**
- AC-1: Dado um projeto cuja política declara `tracker: jira`, `jira_site` e `jira_project`, quando o dono importa um ticket, então o card nasce com referência `jira`, ator `Actor.Adapter("jira")` e o tracker usado é o `AcliTracker`; sem a chave `tracker`, o padrão continua `github`.
- AC-2: Dado um card com referência `jira`, quando o fechamento é confirmado e quando um marco é escrito, então o comentário vai para o ticket pelo `AcliTracker`, sem texto vindo do ticket.
- AC-3: Dado um card Jira, quando o rascunho de fechamento e o marco `pr_opened` montam o link do PR, então o link vem do forge e da referência do repositório, não do sufixo `/issues/<id>` da url do tracker.
- AC-4: Dado uma política com `tracker` fora de `github|jira`, ou com `tracker: jira` sem `jira_site` ou `jira_project`, quando o projeto é registrado, então é recusada nomeando a chave.

**Fora de escopo:** sincronização contínua com o Jira; outros trackers além de github e jira; mudar o adaptador `AcliTracker`; escolher o tracker por card (é por projeto); migrar cards já existentes.

**Aderência ao produto:** serve "importar e fechar, sem sincronização contínua" e o público da empresa com Jira; o tracker externo só é tocado na importação, nos marcos e no fechamento confirmado. Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core`: `ProjectPolicies` ganha `tracker: TrackerConfig` (sealed: `GitHub`, `Jira(site, project)`), com `provider: String` (`"github"` ou `"jira"`). `from()` lê `tracker`, `jira_site`, `jira_project`; valor fora de `github|jira`, ou `jira` sem `jira_site`/`jira_project`, lança `InvalidPolicyException(field)`. Sem a chave: `GitHub`. `DEFAULT` usa `GitHub`.
- `daemon`: `ProjectRegistry.POLICY_KEYS` inclui as três chaves novas; o erro existente já nomeia o campo.
- `daemon`: `ProjectDelivery.trackerOf` devolve `AcliTracker(site, project, workDir = repo, tmpDir, timeout)` quando a política é `Jira`, senão o `GhTracker` atual. Novo `ProjectDelivery.pullRequestUrl(project, pr): String?`, montado do slug do origin (o mesmo `originSlug` do forge) como `https://github.com/<slug>/pull/<pr>`; `null` sem origin GitHub.
- `daemon`: `CardImport`, `CardClosure` e `ExternalMilestones` trocam a constante `PROVIDER = "github"` por `project.policies.tracker.provider` (ator `Actor.Adapter(provider)`; a referência procurada é a do provider do projeto). `TextChangeWarning` recebe o provider por parâmetro.
- `daemon`: `CardClosure` e `ExternalMilestones` recebem `prLinkOf: (Project, String) -> String?` no lugar do `removeSuffix("/issues/...")`; sem link, o rascunho recusa com mensagem que nomeia o card e o marco `pr_opened` não é escrito. Fiação em `Main.kt` e `RefineRuntime.kt`.

**Camadas (core, daemon) e arquivos (8):** `core/.../ProjectPolicies.kt`; `daemon/.../ProjectRegistry.kt`, `ProjectDelivery.kt`, `CardImport.kt`, `CardClosure.kt`, `ExternalMilestones.kt`, `Main.kt`, `RefineRuntime.kt`. `adapters` não muda.

**Migração:** nenhuma; projetos sem `tracker` seguem `github`. Cards existentes têm referência `github`, que continua o provider do projeto.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Chaves de política `tracker`, `jira_site`, `jira_project` (planas, como `delivery_mode`): o `AcliTracker` exige site e chave de projeto, e o registro já recusa por campo.
- Link do PR vem do slug do origin (`github.com/<slug>/pull/<n>`), pois o forge é só GitHub; evita alterar a port `Forge`.
- Os testes do `core` (`ProjectPoliciesTest`) cobrem o parse junto; o plano usa o teste do daemon por AC, que exercita o fluxo observável.
- Peso `heavy`: 2 camadas.
- Fechamento sem link de PR recusa em vez de gravar link quebrado.

## Open questions
- none

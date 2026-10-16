# Forge GitHub via gh — spec

**Issue:** #44 · **Branch:** `feat/44-forge-github-via-gh` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** a port de forge (#43) só tem fake; sem uma implementação real a fábrica não consegue abrir PR nem mergear no GitHub, e a entrega não sai da máquina.
**Audience:** o desenvolvedor dentro de uma empresa (PR com convenções, revisão humana ou merge autônomo conforme o projeto); também o desenvolvedor sozinho no modo autônomo.
**Acceptance criteria**
- **AC-1** — Dado um card validado e liberado para entrega, quando a fábrica abre o PR pelo `gh`, então o PR existe no GitHub com o título e o corpo nas convenções do projeto e a URL/número é devolvido.
- **AC-2** — Dado um PR aberto e um head SHA esperado, quando a fábrica mergeia, então o merge é squash e só acontece se o head do PR for exatamente o SHA informado.
- **AC-3** — Dado que o head do PR difere do SHA informado, quando a fábrica tenta mergear, então o merge é recusado com erro que diz o PR, o SHA esperado e o encontrado, e o PR permanece intacto.
- **AC-4** — Dado erro de rede ou falha do `gh` (saída não zero, sem autenticação, `gh` ausente), quando qualquer operação do forge roda, então o erro é claro, cita o id do card/PR sem vazar token nem texto de issue, e nada fica meio gravado.
- **AC-5** — Dado que a abertura do PR já aconteceu antes (retentativa após falha), quando a fábrica repete a operação, então não nasce PR duplicado.
- **AC-6** — Dado o comportamento observável, quando a mesma sequência roda contra o fake da port e contra o adapter (com `gh` simulado), então os resultados e erros são equivalentes.
**Out of scope:** GitLab e outros forges; sincronização contínua com o GitHub; decidir quando entregar (política do projeto); criação de branch e push.
**Product fit:** serve "a fábrica é dona do estado; GitHub é tocado só em marcos e no fechamento" e "nada sai da máquina sem evento assinado ou política do projeto" (o adapter só executa; a autorização fica a montante). Não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:** implementa `sfv2.ports.Forge` sem alterá-la (`openPullRequest`, `checks`, `merge`, `findOpenPullRequest`, `state`); erros viram `ForgeException` (mensagem com operação e número do PR, nunca corpo nem log de CI) e `UnknownPullRequestException`.

**Camada/módulo:** só `adapters`. Arquivos:
- `adapters/src/main/kotlin/sfv2/adapters/GhForge.kt`: `class GhForge(repo: String, gh: String = "gh", workDir: Path, tmpDir: Path, timeout: Duration, runner: ProcessRunner = ProcessRunnerImpl()) : Forge`. Roda `gh` pelo `ProcessRunner` existente (stdout em arquivo, timeout com kill da árvore) com `GH_PROMPT_DISABLED=1`; o token vem do ambiente do `gh`, nunca é lido nem logado.
- `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt`: `gh` simulado por script sh (padrão de `ClaudeRuntimeFixtures.aScript`), fixtures em `object GhForgeFixtures`.

**Comportamento:**
- `findOpenPullRequest(branch)`: `gh pr list --repo R --head B --state open --json number,headRefName,headRefOid`.
- `openPullRequest`: antes de criar, chama `findOpenPullRequest(branch)` e devolve o PR existente (idempotência, AC-5); senão `gh pr create --repo R --head B --base BASE --title T --body-file F` (corpo num arquivo em `tmpDir`, apagado no `finally`, nunca em argv) e relê o PR por `findOpenPullRequest` para obter número e head.
- `merge(pr, expectedHead)`: `gh pr view --json state,headRefOid,mergeable` → `NotOpen` se não estiver aberto, `HeadMismatch(actual)` se o head difere (sem chamar o merge), `Conflict` se `CONFLICTING`; senão `gh pr merge N --squash --match-head-commit EXPECTED` (a guarda do próprio GitHub cobre a corrida entre a leitura e o merge); mergeCommit lido de `mergeCommit.oid` em `gh pr view`.
- `checks(head)`: `gh api repos/R/commits/HEAD/check-runs` → `Pending` se algum não concluído, `Failed` com `FailedCheck(name, summary)` (summary cortado em 2048 bytes UTF-8), senão `Passed`.
- `state(pr)`: `gh pr view --json state,headRefOid` → `Open/Merged/ClosedUnmerged`; PR inexistente → `UnknownPullRequestException`.
- Falha (saída não zero, `TimedOut`, `gh` ausente por `IOException`) → `ForgeException("<operação> falhou (pr=<n>|branch=<b>)")` sem stderr bruto. Cada operação é de uma chamada mutável só (create ou merge), logo nada fica meio gravado; o único artefato local é o arquivo do corpo, apagado no `finally`.
- Parse de JSON com `kotlinx.serialization.json` (já dependência do módulo); sem dependência nova.

**Migração:** n/a. Sem dados persistidos.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |
| AC-6 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhForgeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo `GhForgeTest.kt`: nenhuma suíte do módulo cobre o forge.
- Idempotência de abertura via `findOpenPullRequest` antes de criar: a port já o oferece e evita PR duplicado na retentativa.
- Merge com `--match-head-commit` além da checagem prévia: fecha a corrida entre leitura e merge.
- Corpo do PR por `--body-file`: evita limite de argv e vazamento em listagem de processos.
- Peso `standard`: uma camada, 6 ACs, 2 arquivos, sem UI nem segurança.

## Open questions
- none

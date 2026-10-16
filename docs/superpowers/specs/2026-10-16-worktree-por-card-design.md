# Worktree por card — spec

**Issue:** #24 · **Branch:** `feat/24-worktree-por-card` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema.** Vários cards rodam ao mesmo tempo na mesma máquina; sem um checkout isolado por card, o trabalho de um agente pisa no de outro e no repositório do dono.

**Audiência.** O desenvolvedor sozinho com muitos projetos (até dez numa máquina, cards em paralelo). Serve também ao desenvolvedor de empresa, cuja convenção de branch deve ser respeitada.

**Critérios de aceitação**
- AC-1: Dado um card que entra num estágio que exige código, quando o daemon prepara o estágio, então existe um worktree do card com uma branch nomeada pelo padrão de convenções do projeto.
- AC-2: Dado um card cujo worktree e branch já existem, quando ele entra em outro estágio ou o estágio é repetido, então o mesmo worktree é reaproveitado, sem recriar nem perder o trabalho já feito.
- AC-3: Dado um card que sai do board (Done ou descartado), quando a saída é registrada, então o worktree e a branch local do card são removidos.
- AC-4: Dado dois cards ativos no mesmo projeto, então cada um tem seu próprio worktree e branch, sem compartilhar diretório.
- AC-5: Dado um worktree com alterações não commitadas, quando o card sai do board, então a limpeza não descarta esse trabalho em silêncio: o worktree e a branch ficam, o resultado informa a recusa e o daemon segue de pé.
- AC-6: Dado um `project.name` ou um `cardId` que não é um segmento de caminho seguro (`[A-Za-z0-9][A-Za-z0-9._-]*`, sem `..`, sem `/` nem `\\`), quando `ensure` ou `cleanup` é chamado, então o gerenciador recusa com erro que nomeia o campo (`project.name` ou `cardId`), nenhum processo `git` roda e nenhum diretório é criado ou removido.

**Fora de escopo**
- Push da branch, PR e remoção de branch remota (entrega).
- Execução de agentes dentro do worktree (runtime, outros itens da fase 2).
- Limpeza de worktrees órfãos sem card correspondente.
- Configuração de convenção de branch além do padrão já definido para o projeto.

**Adequação ao produto.** Serve ao princípio "a fábrica é dona do próprio estado" e à operação com o app fechado: o daemon cuida do ciclo de vida do checkout sem tocar nada externo. Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `daemon`.

**Contratos** (pacote `sfv2.daemon`):
- `interface WorktreeGit`: `fun addWorktree(repo: Path, dir: Path, branch: String)`, `fun hasWorktree(repo: Path, dir: Path): Boolean`, `fun isDirty(dir: Path): Boolean`, `fun removeWorktree(repo: Path, dir: Path)`, `fun deleteBranch(repo: Path, branch: String)`. `SystemWorktreeGit` roda `git -C` via `ProcessBuilder` com timeout e `GIT_TERMINAL_PROMPT=0`, como `GitProjectConfigSource`. A branch nasce de `origin/main`; se `origin/<branch>` existir, é reaproveitada.
- `data class CardWorktree(val cardId: String, val path: Path, val branch: String)`.
- `sealed interface CleanupResult`: `Removed`, `KeptDirty(path)`, `NothingToClean`.
- `class WorktreeManager(root: Path, git: WorktreeGit, log: DaemonLog = StderrDaemonLog)`: `ensure(project: Project, cardId: String, title: String): CardWorktree` (idempotente) e `cleanup(project: Project, cardId: String, title: String): CleanupResult`. Métodos `@Synchronized`.
- Branch: `project.policies.conventions.branchFormat` com `{id}` = id do card e `{slug}` = título em minúsculas, ascii, hífens, máx. 40 caracteres. Diretório: `<root>/<project.name>/<cardId>`. O `root` vem por parâmetro (a casa do projeto fica para o dispatcher).
- Falha do git vira `WorktreeException` (RuntimeException) com o id do card; `cleanup` com alterações não commitadas não lança: devolve `KeptDirty`. Log `key=value` só com `card`, `path`, `branch`; nunca título ou texto de issue.

**Arquivos:** novos `daemon/src/main/kotlin/sfv2/daemon/WorktreeManager.kt` e `WorktreeGit.kt`; teste novo `WorktreeManagerTest.kt`. Ligar `ensure`/`cleanup` às transições de estágio e à saída do board fica para o card do dispatcher (#27); aqui são funções chamáveis.

**Migração:** nenhuma; sem mudança de store, evento ou contrato público.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorktreeManagerTest.kt` |

## Security
_Revisão do dono interino_
- **`project.name` vem do `factory.yml` e o diretório do worktree é `<root>/<project.name>/<cardId>`:** sem validação, um nome com `..` levaria a criação e a remoção para fora de `root` (AC-6). O caminho resolvido precisa ficar dentro de `root` (`normalize` + `startsWith`) antes de qualquer `git` ou remoção.
- **A limpeza só remove o que o gerenciador criou:** `removeWorktree` recusa diretório que não seja worktree registrado do repositório; nunca apaga por `rm -r` do caminho.
- **O slug da branch vem do título (texto externo):** só `[a-z0-9-]`, máx. 40 caracteres, aplicado dentro do `branchFormat` já validado no #19; nunca o título cru em argumento ou log.
- **`git` sem shell**, com timeout, `GIT_TERMINAL_PROMPT=0` e `-c core.fsmonitor=false`; hooks do repositório do dono rodam como sempre (`post-checkout`), o que é aceito porque o repositório é registrado pelo dono.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Cortei os eventos de criação/limpeza propostos pelo PO (antigo AC-6): exigiriam novo tipo de evento em `core` (peso heavy) sem ser pedido na issue; o log estruturado cobre a rastreabilidade.
- Limpeza com alterações não commitadas mantém worktree e branch (`KeptDirty`): descartar trabalho em silêncio é irreversível.
- `WorktreeGit` é a port local do daemon, com fake nos testes de `WorktreeManagerTest`; os testes de `SystemWorktreeGit` usam um repositório git temporário real, pois o git é local e determinístico.
- Teste novo `WorktreeManagerTest.kt`: nenhuma suíte do `daemon` cobre worktrees.
- Fonte do `root` e ligação ao ciclo de estágios ficam para o dispatcher (#27).

## Open questions
- none

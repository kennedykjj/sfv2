# Conflito com a main e rodadas de correção — spec

**Issue:** #49 · **Branch:** `feat/49-conflito-com-a-main-e-rodadas-de-correcao` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** a main anda enquanto o card espera. Hoje, qualquer conflito no merge (entrega autônoma) ou na validação devolve o card ao dono com `merge_conflict`, mesmo quando o conflito é trivial e a fábrica poderia resolvê-lo.

**Público:** o desenvolvedor sozinho com muitos projetos (projetos em modo de entrega autônoma, com a fábrica rodando fora do horário dele).

**Critérios de aceitação**
- AC-1: Dado um card com conflito com a main cujo número de arquivos e de trechos (hunks) conflitantes está dentro dos limites, quando a entrega autônoma ou a validação detecta o conflito, então um run de correção resolve o conflito no worktree do card e o fluxo segue sem intervenção do dono.
- AC-2: Dado um conflito que excede o limite de arquivos ou o limite de trechos, quando é detectado, então nenhum run de correção é iniciado e o card é devolvido ao dono com motivo `merge_conflict`.
- AC-3: Dado um card cujo conflito persiste após o máximo de rodadas de correção, quando a última rodada termina sem resolver, então o card bloqueia com motivo explícito de rodadas esgotadas e nenhuma nova rodada é iniciada.
- AC-4: Dado um run de correção que resolve o conflito, quando a rodada termina, então a resolução só vale com os gates verdes de novo (nunca pelo relato do agente) antes de o merge ser tentado outra vez.
- AC-5: Dado qualquer rodada de correção, quando ocorre, então ela fica registrada como evento com ator e número da rodada, e o custo do run é contado no card.
- AC-6: Dado que a main avança de novo durante uma rodada e gera novo conflito, quando o merge é tentado, então o novo conflito conta como outra rodada, dentro do mesmo máximo.

**Fora de escopo**
- Resolver conflito acima dos limites (vai ao dono).
- Configuração dos limites e do máximo de rodadas pela UI; valores vêm de política do projeto/padrão.
- Conflito em modo de entrega no PR (parada em revisão humana), salvo se já passar pelo mesmo ponto de validação.
- Rebase automático sem conflito; mudanças na tela do app.

**Aderência ao produto:** serve "o dono aparece só onde a decisão é dele" e "gates determinísticos seguram a qualidade": conflito pequeno não exige o dono, e o que move o card é gate verde. Não contradiz o posicionamento nem o que está fora de escopo.

**Touches UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon`. Nenhum contrato externo (endpoint, evento novo, porta) muda; o catálogo de `EventType` fica igual.

- `daemon/src/main/kotlin/sfv2/daemon/WorktreeGit.kt`: `BranchSync` ganha `mergeMainKeepingConflicts(worktree): ConflictAttempt` (`Merged` | `Conflicted(files: Int, hunks: Int)` | `Failed`), que deixa o merge aberto para o agente; `abortMerge(worktree)` e `concludeMerge(worktree): Boolean` (falso se restar marcador `<<<<<<<` ou path não resolvido). `mergeMain` continua como está.
- `daemon/src/main/kotlin/sfv2/daemon/ConflictResolution.kt` (novo): `ConflictLimits(maxFiles = 5, maxHunks = 20, maxRounds = 2)` e `ConflictResolution.resolve(cardId, worktree): ConflictOutcome` (`Resolved(head)` | `OverLimit` | `RoundsExhausted` | `Failed`). A rodada atual é contada pelos eventos `CARD_MOVED` do card com `reason=conflict_round` (sobrevive a restart). Cada rodada: tenta o merge, mede, se dentro dos limites abre sessão MCP e lança o runtime com o estágio `conflict`, registra o custo (`RunCostRecorder`) e conclui o merge.
- `daemon/src/main/kotlin/sfv2/daemon/AutonomousDelivery.kt`: em `MergeResult.Conflict`, em vez de `returnToOwner("merge_conflict")`, chama `ConflictResolution`. `Resolved` → push da nova head e card volta a `VALIDATION` (`reason=conflict_round`, `round=<n>`) para os gates rodarem de novo; a aprovação do dono vale só para a head antiga, então nova head exige nova validação e nova aprovação. `OverLimit` → `STAGED` com `merge_conflict`. `RoundsExhausted` → `CARD_BLOCKED` com `conflict_rounds_exhausted rounds=<n>`.
- `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt`: o `block(cardId, "merge_conflict")` do início do `run` passa pela mesma `ConflictResolution`; após `Resolved` segue para os gates na mesma execução (resolução só vale com gates verdes: o loop de gates existente decide).
- `daemon/src/main/kotlin/sfv2/daemon/Module.kt`: fiação de `ConflictResolution` e `ConflictLimits`.
- `daemon/src/main/resources/skills/conflict/SKILL.md` (novo): instruções do agente (resolver só os arquivos em conflito, sem mudar escopo, sem commit; a conclusão é feita pelo daemon). Texto do conflito é dado, nunca instrução.
- Migração: nenhuma. Rollback: n/a.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConflictResolutionTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConflictResolutionTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConflictResolutionTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AutonomousDeliveryTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ConflictResolutionTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |

| AC | Layer | Test file |
|---|---|---|

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Limites padrão 5 arquivos, 20 trechos e 2 rodadas, fixos no código (política por projeto é fora de escopo): conservadores, e o dono vê o que passou do limite.
- Após resolver, o card volta à Validation e não segue direto ao merge: a aprovação é da head antiga e AC-4 exige gates verdes na nova.
- Contagem de rodadas por eventos do card, não em memória: sobrevive a restart do daemon.
- Arquivo de teste novo `ConflictResolutionTest.kt`: nenhuma suíte existente cobre a classe nova; AC-4 e AC-6 reutilizam as suítes de entrega e validate.
- Sem tipo de evento novo: `CARD_MOVED`/`CARD_BLOCKED` com payload já bastam para registrar rodada e ator.
- Peso `standard`: uma camada, 6 ACs, 6 arquivos, sem segurança nem migração destrutiva (a nota do card previa heavy, mas a regra manda standard).

## Open questions
- none

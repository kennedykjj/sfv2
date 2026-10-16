# RunPool: só soltar o lock órfão depois de gravar o evento — spec

**Issue:** #129 · **Branch:** `feat/129-runpool-so-soltar-o-lock-orfao-depois-de-g` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** no `RunPool.sweep()` o lock órfão sai da memória antes de o evento `LOCK_RELEASED` ser gravado. Se a gravação falha (banco indisponível), o lock some, a exceção interrompe a varredura dos demais e a liberação nunca vira evento: o próximo ciclo não encontra mais o lock para tentar de novo.
**Audience:** o desenvolvedor sozinho com muitos projetos (e o que está numa empresa), que depende da trilha de eventos do daemon para saber que um card foi liberado.
**Acceptance criteria**
- **AC-1** — Given um lock órfão e uma falha ao gravar o evento, when `sweep` roda, then o lock permanece no pool e o erro é registrado em log com o id do card, sem o conteúdo do erro.
- **AC-2** — Given vários locks órfãos e a gravação falhando só no primeiro, when `sweep` roda, then os demais são liberados e seus eventos gravados.
- **AC-3** — Given a falha resolvida, when o próximo `sweep` roda, then o lock pendente é liberado e o evento gravado uma única vez.
**Out of scope:** persistir locks entre reinícios do daemon.
**Product fit:** serve ao princípio "toda ação é um evento com ator" e a "a fábrica é dona do próprio estado": liberar lock sem evento quebra a trilha de auditoria. Não contradiz posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contratos: `RunPool.sweep(): List<RunLock>` mantém a assinatura e passa a devolver só os locks efetivamente liberados (lock cuja gravação falhou fica no pool e fora da lista). Nenhum endpoint ou evento novo.
- Camada e arquivos: só `daemon`. `daemon/src/main/kotlin/sfv2/daemon/RunPool.kt` (ordem: gravar o evento primeiro, remover o lock só depois; falha por lock capturada, logada como `lock_release_failed card=<id>` sem a mensagem da exceção, e a varredura segue para os demais). `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` (testes e fake de gravação).
- Gravação do evento: `recordRelease` continua idempotente por ciclo, pois o evento só é gravado uma vez por lock liberado; lock sem card (`lock_release_orphan_card`) continua sendo removido, pois não há o que gravar.
- Injeção de falha nos testes: `CardEventStore` é classe final da camada `store`; o `RunPool` passa a depender de uma interface pequena do daemon (`LockEventRecorder`: buscar o card e gravar a mudança) com adaptador padrão sobre `CardEventStore`, para o teste injetar um fake que falha por card. O construtor aceita o `CardEventStore` como hoje (sobrecarga/factory) para não quebrar os chamadores existentes.
- Migração: n/a.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `light`: uma camada, 3 critérios, 2 arquivos, sem UI nem segurança.
- Interface `LockEventRecorder` no daemon em vez de tornar `CardEventStore` aberta: evita mexer na camada `store` e permite falha seletiva por card.
- Lock cujo card não existe é removido mesmo assim: não há evento a gravar e reter o lock o prenderia para sempre.
- Sem novo arquivo de teste: `RunPoolTest.kt` já cobre o `RunPool`.

## Open questions
- none

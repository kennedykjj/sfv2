# Interface Tracker e fake — spec

**Issue:** #52 · **Branch:** `feat/52-interface-tracker-e-fake` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o código da fábrica precisa importar de e fechar em trackers externos (GitHub, Jira) sem depender de nenhum deles. Sem uma interface única e um fake de teste, cada fase posterior acopla ao tracker real e não dá para testar sem rede.

**Audiência:** o desenvolvedor dentro de uma empresa (Jira, GitHub), onde a sfv2 importa e fecha. O desenvolvedor solo também se beneficia ao importar issues do GitHub.

**Critérios de aceitação**
- AC-1: Dado a interface Tracker, quando peço um item por id, então recebo o item importado (ou ausência explícita se não existir).
- AC-2: Dado a interface Tracker, quando busco por um critério, então recebo a lista de itens correspondentes.
- AC-3: Dado a interface Tracker, quando escrevo um marco em um item, então o marco fica registrado nesse item.
- AC-4: Dado a interface Tracker, quando fecho um item com um comentário, então o item fica fechado e o comentário registrado.
- AC-5: Dado o tracker falso, quando um teste executa importar, buscar, marcar e fechar, então o teste lê quantas chamadas de cada operação aconteceram.
- AC-6: Dado o tracker falso, quando um teste o usa, então nenhuma rede nem tracker real é tocado e o resultado é determinístico.
- AC-8: Dado um item buscado, quando se lê o `textHash`, então ele é o SHA-256 de título e corpo, igual entre dois itens com o mesmo texto e diferente quando o corpo muda.
- AC-9: Dado um item já fechado, quando se fecha de novo com outro comentário, então o resultado é `AlreadyClosed` e nenhum comentário novo é gravado.
- AC-10: Dado uma url que não é `https://`, que passa de 2048 caracteres ou tem caractere de controle, quando o item é construído, então a construção falha com `TrackerException`.
- AC-7: Dado texto vindo do tracker (título, corpo, comentário), quando ele é devolvido pela interface, então chega marcado como não confiável.

**Fora de escopo:** adaptadores reais de GitHub ou Jira; sincronização contínua; autenticação e credenciais; decidir quando marcar ou fechar (política e assinatura do dono); qualquer UI.

**Encaixe no produto:** serve "importar e fechar, sem sincronização contínua" e "texto externo entra por uma porta só, marcado como não confiável". Não contradiz o posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `ports` (depende só de `core`). Nenhuma migração, nenhum endpoint, nenhum evento.

Contratos novos, no pacote `sfv2.ports`:
- `Tracker` (`ports/src/main/kotlin/sfv2/ports/Tracker.kt`): `fetch(id: String): TrackerItem?` (null = não existe), `search(query: String): List<TrackerItem>`, `writeMilestone(id: String, milestone: String)`, `close(id: String, comment: String): CloseResult` (`Closed` ou `AlreadyClosed`; fechar um item já fechado não grava um segundo comentário, para o #56 repetir o fechamento depois de uma falha sem duplicar). Falha de rede ou API lança `TrackerException` (mensagem com operação e id, nunca texto do item); id desconhecido em `writeMilestone`/`close` lança `UnknownTrackerItemException`.
- `TrackerItem(id, title: ExternalText, body: ExternalText, labels: List<ExternalText>, url: String, state: TrackerItemState, textHash: String)`; `TrackerItemState` é enum `OPEN`/`CLOSED`. `textHash` é o SHA-256 hexadecimal de `title + NUL + body`, calculado pela fábrica `TrackerItem.of(...)` (pura) para que adaptador e fake concordem: o #53 devolve o hash junto do texto e o #55 compara o hash importado com o atual para avisar do texto alterado. `url` só aceita `https://` com até 2048 caracteres e sem caractere de controle (vem de fora; `TrackerException` ao construir um item com url inválida).
- `ExternalText(value: String)`: value class que marca texto vindo de fora; `toString()` não revela o valor (não vaza em log). Nome diferente de propósito: o daemon já tem `sfv2.daemon.UntrustedText(source, text)`, o envelope do MCP (#57 embrulha um `ExternalText` nesse envelope); com o mesmo nome os dois tipos colidiriam nos imports.
- `FakeTracker` (`ports/src/main/kotlin/sfv2/ports/FakeTracker.kt`): itens em memória, sem IO nem relógio; `seed(item)`, `failNext(operation)` (a falha não altera o estado); contadores `calls` (mapa operação → quantidade; toda chamada conta, inclusive a que falha por `failNext`), `milestones` (id → lista, em ordem) e `closures` (id → comentário).

Arquivos: `Tracker.kt`, `FakeTracker.kt`, `TrackerTest.kt` (3 no total).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-2 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-3 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-4 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-5 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-6 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-7 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-8 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-9 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |
| AC-10 | ports | `ports/src/test/kotlin/sfv2/ports/TrackerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy` pela regra mecânica (mais de 6 critérios), apesar de ser uma camada e 3 arquivos; o dono pode baixar para `light` na revisão.
- Arquivo de teste novo `TrackerTest.kt`: nenhuma suíte de `ports` cobre o Tracker; segue o par `ForgeTest`/`RuntimeTest`.
- AC-7 vira o tipo `ExternalText` em `ports`, não convenção em comentário: o compilador impede tratar título/corpo como `String` comum. A porta única de texto externo (#57) o embrulha no envelope `UntrustedText` do daemon.
- Marco é `String` livre: o catálogo de marcos ("comecei", "PR aberto") é decisão do #55, não da porta.
- O fake segue o estilo do `FakeForge` (`failNext`, listas observáveis), para os testes dos outros módulos usarem igual.
- Fazer `search` receber `String` simples: a sintaxe de busca do GitHub e do Jira difere e é problema dos adaptadores (#53, #85).

## Open questions
- none

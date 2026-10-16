# Rascunho e confirmação do fechamento — spec

**Issue:** #56 · **Branch:** `feat/56-rascunho-e-confirmacao-do-fechamento` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** o fim do card é a sua voz na ferramenta oficial. Hoje um card em Done com referência externa não tem como virar um comentário de fechamento no tracker sem a fábrica escrever fora da máquina por conta própria.
**Audience:** o desenvolvedor dentro de uma empresa (GitHub/Jira), que decide e assina o que sai da máquina.
**Acceptance criteria**
- **AC-1** — Dado um card em Done com referência externa (github), quando o rascunho é pedido, então o daemon devolve status, resumo e link do PR, e nada é escrito no tracker.
- **AC-2** — Dado um card que não está em Done ou não tem referência externa, quando o rascunho ou a confirmação é pedido, então o pedido é recusado com motivo e nada é escrito.
- **AC-3** — Dado a confirmação do dono, quando o daemon a processa, então o evento assinado (prova do dono) é gravado antes da escrita no tracker, e a escrita é feita por código (`Tracker.close`), nunca por um agente.
- **AC-4** — Dada uma confirmação sem prova válida do dono (ator não dono), quando chega ao daemon, então é recusada, nenhum evento de confirmação é gravado e nada é escrito no tracker.
- **AC-5** — Dada a escrita no tracker falhar, então o card mostra o erro do fechamento e continua em Done, com a confirmação já registrada.
- **AC-7** — Dado um card entregue no modo PR (sem `PR_MERGED`, com `pr` no movimento para Done), quando o rascunho é pedido, então o link vem desse `pr` e o rascunho não tem commit; sem nenhum dos dois, é recusado com `no_pr_recorded`.
- **AC-8** — Dado um card cujo título tem `@time`, links ou markdown, quando o rascunho é gerado e confirmado, então o comentário escrito no tracker não contém o título.
- **AC-6** — Dado um fechamento com erro, quando o dono tenta de novo, então a escrita é repetida sem novo evento de confirmação e sem comentário duplicado; um fechamento que já aconteceu no tracker conta como escrito.
**Out of scope:** botões na CLI/app (UI), marcos opcionais (#55), porta única do texto externo (#57), Jira.
**Product fit:** serve "toda ação é um evento com ator; a aprovação do dono é um evento assinado" e "nada sai da máquina sem evento assinado pelo dono"; fechamento confirmado é um dos toques permitidos no tracker. Não contradiz posicionamento nem escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contratos: `core` ganha 3 `EventType`: `CLOSURE_CONFIRMED` (ator dono; payload `draft_hash`), `CLOSURE_WRITTEN` (ator fábrica; payload `result`: `closed` ou `already_closed`) e `CLOSURE_FAILED` (ator fábrica; payload `reason` curto, sem texto da issue). Porta `Tracker` inalterada.
- `daemon`: nova classe `CardClosure(store, recorder, registry, trackerOf, ownerProof, clock, log)`, mesmo formato do `CardImport`. Dois comandos em `Protocol.kt`: `card.close.draft` (`Request.CardCloseDraft(id)`, só leitura, sem prova) e `card.close.confirm` (`Request.CardCloseConfirm(actor, id, draftHash, proof)`); resposta `Response.CloseDraftResult(status, summary, prLink, draftHash)`. Roteados em `SocketServer` e tratados como "not a card command" em `CardCommands`.
- Rascunho: exige card em `DONE` com `ExternalRef` de provider `github`; status = coluna final, resumo = id do card + commit do merge; link do PR = `<base da url da referência externa, sem o sufixo /issues/<id>>/pull/<número>` (o `Project.repo` é um caminho de pasta local, não um repositório do GitHub). O número do PR e o commit vêm do último `PR_MERGED` ou, nos cards entregues no modo PR (que não têm `PR_MERGED`), do campo `pr` do movimento para Done (sem commit: o resumo omite o commit). Sem número de PR em nenhuma das duas fontes, o rascunho é recusado com `no_pr_recorded`. O texto que vai para o tracker nunca contém texto vindo de fora: o título do card (que veio da issue e pode trazer menções `@time` e links) não entra no comentário postado, que leva só id do card, commit e link do PR; o dono vê o título só no rascunho local. Nenhum texto do corpo da issue entra. `draftHash` = SHA-256 do rascunho.
- Confirmação: recusa não dono / prova inválida (AC-4) e card inelegível (AC-2); recusa se `draftHash` não bate com o rascunho atual. Grava `CLOSURE_CONFIRMED` antes de qualquer chamada; só então chama `tracker.close(externalId, comentário)`. Sucesso (`Closed` ou `AlreadyClosed`) grava `CLOSURE_WRITTEN`; `TrackerException` grava `CLOSURE_FAILED` e devolve o erro; o card continua em Done.
- Nova tentativa (AC-6): o mesmo `card.close.confirm`. Se já há `CLOSURE_CONFIRMED` com o mesmo `draftHash` e sem `CLOSURE_WRITTEN` posterior, reaproveita-o (sem novo evento) e repete a escrita; com `CLOSURE_WRITTEN` já gravado devolve sucesso sem chamar o tracker. `Tracker.close` só comenta quando fecha, então a repetição não duplica o comentário.
- O erro aparece no card pelo último `CLOSURE_FAILED` nos eventos do card (leitura já existente via `card.show`/stream); sem mudança de armazenamento.
- Arquivos: `core/src/main/kotlin/sfv2/core/Event.kt` (editado), `daemon/src/main/kotlin/sfv2/daemon/CardClosure.kt` (novo), `Protocol.kt`, `SocketServer.kt`, `CardCommands.kt`, `Main.kt` (editados), `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` (novo). Donos: `core` e `daemon`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardClosureTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Os 3 critérios da issue viram 6: recusa de não dono e de card fora de Done/sem referência externa separadas, retry sem duplicar separado da exibição do erro.
- Sem botões de CLI/UI: a feature é só daemon, conforme o Tech Lead.
- Peso `heavy` (a issue esperava `standard`): os 3 novos `EventType` tocam `core` além de `daemon`, 2 camadas.
- Arquivo de teste novo `CardClosureTest.kt`: nenhuma suíte do daemon cobre o fechamento (`CardImportTest` é da importação); os eventos novos de `core` são verificados pelo daemon.
- O link do PR é montado do repositório do projeto e do número do `PR_MERGED`: o `ExternalRef` aponta a issue, não o PR, e o card só guarda o número.
- A confirmação carrega o `draftHash`: o dono confirma exatamente o texto que leu; rascunho mudado exige novo pedido.
- Nova tentativa é o próprio `card.close.confirm`, que ainda exige a prova do dono.

## Open questions
- none

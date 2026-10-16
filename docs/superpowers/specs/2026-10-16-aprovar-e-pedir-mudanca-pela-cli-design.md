# Aprovar e pedir mudança pela CLI — spec

**Issue:** #42 · **Branch:** `feat/42-aprovar-e-pedir-mudanca-pela-cli` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o dono precisa tomar pela CLI as duas decisões que são só dele na Spec Review (aprovar a spec ou pedir mudança), deixando prova assinada, e a fase 2 precisa de um teste de saída que mostre o fluxo de ponta a ponta.

**Audiência:** o desenvolvedor sozinho com muitos projetos; também o desenvolvedor em empresa, que precisa da trilha de auditoria da sua assinatura.

**Critérios de aceitação**
- **AC-1** — Dado um card em Spec Review, quando o dono aprova pela CLI, então o card fica em Ready for Dev e existe um evento `OWNER_APPROVED` com o dono como ator.
- **AC-2** — Dado um card em Spec Review, quando o dono pede mudança pela CLI com uma nota, então o card volta a Ready for Refinement e a nota fica registrada no evento.
- **AC-3** — Dado um card em Spec Review, quando o dono pede mudança sem nota, então o comando é recusado com mensagem clara e o card não muda de coluna nem gera evento.
- **AC-4** — Dado um card fora de Spec Review (ou inexistente), quando o dono aprova ou pede mudança, então o comando falha informando o id e a coluna atual, sem mudar o card.
- **AC-5** — Dado um card aprovado ou devolvido, quando o dono consulta `events`, então a decisão aparece com ator dono, momento e, no caso de mudança, a nota.
- **AC-6** — Dado o teste de saída da fase (camada e2e, runtime falso), quando um card percorre Inbox até Staged, então as únicas intervenções são as aprovações do dono, o custo está registrado e cada gate aparece como evento.

**Fora de escopo:** aprovação de Ready for Prod (outro item); app desktop; editar a spec pela CLI; aprovar em nome de outro ator ou em lote; notificações; sincronizar a decisão com GitHub ou Jira.

**Product fit:** serve "toda ação é um evento com ator; a aprovação do dono é um evento assinado" e "o dono aparece só onde a decisão é dele". Não contradiz posicionamento nem o que está fora de escopo.

**Touches UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- Protocolo do daemon (`daemon/.../Protocol.kt`): dois comandos novos, `card.approve` `{actor, id, proof}` e `card.request-change` `{actor, id, note, proof}`; viram `Request.CardApprove` e `Request.CardRequestChange` (toString mascara `proof`, como `CardMove`).
- `daemon/.../CardCommands.kt`: `approve` e `requestChange` na mesma região sincronizada de `move`. Ambos exigem prova do dono, card existente e coluna `SPEC_REVIEW`; recusa cita o id e a coluna atual. A transição reutiliza `MoveRules.check` (SPEC_REVIEW→READY_FOR_DEV e SPEC_REVIEW→READY_FOR_REFINEMENT, só dono).
- Eventos gravados por `EventRecorder.record`: aprovar → `OWNER_APPROVED` com payload `from`, `to`; pedir mudança → `CARD_MOVED` com payload `from`, `to` e `note` (o catálogo não tem tipo próprio para mudança pedida; não se acrescenta tipo, ver Decisions). Nota vazia ou só espaços, ou acima de 2048 bytes em UTF-8, é recusada antes de gravar (o payload do evento não pode crescer sem limite; mesmo teto de `ValidationOutcome`). Log só com `cmd` e id do card, nunca a nota.
- CLI (`cli/.../CardCommands.kt`): `card approve <id>` e `card request-change <id> --note <texto>` (`--note` obrigatório), usando `ownerToken()` e `cli.call`, imprimindo `cardLine`.
- `events` já imprime todo evento; AC-5 não exige comando novo.

**Camadas e arquivos**
- `daemon`: `Protocol.kt`, `CardCommands.kt` (e o roteamento do `SocketServer` se ele enumerar requests).
- `cli`: `CardCommands.kt`.
- `e2e`: teste de saída da fase e `e2e/build.gradle.kts`: o módulo só enxerga `core`, `daemon` e `cli` (o `daemon` declara `store`, `ports` e `adapters` como `implementation`, que não vazam), então o teste ganha `testImplementation(project(":store"))`, `:ports` (para o `FakeRuntime`) e `:adapters`; sem isso o `PhaseExitTest` não compila. Os gates dos três pipelines entram como fakes (`RefineGates`, `DevelopGates`, `ValidateGates` falsos) e o repositório do projeto é um repositório git temporário; nenhum comando `./gradlew` roda dentro do teste.
- `core`, `store`, `ports`, `adapters`: sem mudança (tipos e regras de movimento já existem).

**Migração:** nenhuma; evento novo usa tipo existente e payload é mapa livre.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-6 | e2e | `e2e/src/test/kotlin/sfv2/e2e/PhaseExitTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Mudança é feita também no `daemon`, apesar de a nota do card dizer só `cli`: a CLI só repassa comandos e o daemon não tem approve/request-change; sem ele os ACs não são testáveis. Por isso o peso sobe para heavy (3 camadas).
- Pedir mudança grava `CARD_MOVED` com `note`, sem tipo novo: o catálogo de eventos é fechado e a nota cabe no payload; evita mexer em `core`/`store`.
- Nota obrigatória em pedir mudança: sem ela o refine não tem o que retrabalhar.
- AC-5 coberta pelo `events` existente, sem filtro por card: o stream já traz id do card e payload.
- `PhaseExitTest.kt` é arquivo novo: nenhuma suíte e2e cobre o fluxo de pipeline completo (a existente `CardsCliTest` trata persistência de eventos); o runtime falso e os pipelines vêm de #39, #40 e #41, e o teste depende deles.
- Os ACs do PO (6) detalham os 3 da issue; o AC-3 da issue (saída da fase) virou AC-6.

## Open questions
- none

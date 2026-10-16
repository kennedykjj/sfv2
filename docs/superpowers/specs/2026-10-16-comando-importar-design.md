# Comando importar — spec

**Issue:** #54 · **Branch:** `feat/54-comando-importar` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O dono escolhe o que entra na fábrica, mas hoje não há como trazer uma issue do tracker externo para um card local, nem atualizar um card já importado.
**Audience:** O desenvolvedor dentro de uma empresa (Jira/GitHub): importa o que decide trabalhar; o dono é o único que dispara a importação.
**Acceptance criteria**
- **AC-1** — Given uma issue externa existente, when o dono a importa, then nasce um card no Inbox com o título da issue e uma referência externa (provider, id, url, data da importação, hash do texto).
- **AC-2** — Given a importação de AC-1, when concluída, then o card tem um evento de criação cujo ator é o adaptador do provider (`Actor.Adapter(provider)`).
- **AC-3** — Given uma issue já importada, when o dono a importa de novo, then nenhum card novo é criado e o card existente continua único para aquele id externo.
- **AC-4** — Given uma reimportação, when o texto externo mudou, then o card e a referência externa refletem o texto novo (hash atualizado) e um evento da reimportação é registrado.
- **AC-5** — Given uma reimportação, when o texto externo não mudou, then o evento de reimportação é registrado e o card não é alterado.
- **AC-6** — Given uma busca no tracker, when o dono importa o resultado, then cada resultado vira um card no Inbox, sem duplicar os já importados.
- **AC-7** — Given uma issue inexistente ou falha do tracker, when a importação roda, then nenhum card é criado e o erro informa o id externo envolvido.
- **AC-9** — Given duas issues com o mesmo número em projetos diferentes (repositórios diferentes), when o dono importa as duas, then nascem dois cards, um por projeto: a busca de card já importado é limitada aos cards do projeto escolhido, nunca global.
- **AC-10** — Given um título de issue com caracteres de controle, quebras de linha, só espaços ou mais de 200 caracteres, when a issue é importada, then o título do card é normalizado (controles viram espaço, espaços repetidos viram um, corte em 200 caracteres) e, se ficar vazio, vira `Issue <id>`; a importação nunca falha por título.
- **AC-11** — Given uma reimportação com texto alterado de um card que já passou de Ready for Dev (tem ou terá worktree e branch derivados do título), when roda, then o título do card não muda (a branch do PR deriva dele); o evento registra `changed=true` e `title_frozen=true` e a referência ganha o hash novo para o aviso de texto alterado (#55).
- **AC-12** — Given uma busca que traz issues fechadas, when o dono importa o resultado, then as fechadas são puladas (importar por id continua aceitando uma fechada).
- **AC-8** — Given um pedido de importação que não vem do dono, when chega ao daemon, then é recusado e nada é criado.
**Out of scope:** CLI `sfv2 import` (card separado); sincronização contínua com o tracker; importação de comentários ou relações (ex.: Blocked by, tratado no #53); fechamento da issue externa; trackers além dos existentes (Jira etc.).
**Product fit:** Serve "importar e fechar, sem sincronização contínua": o tracker é tocado só na importação; texto externo entra marcado como não confiável por uma porta só; toda ação é um evento com ator. Não contradiz posicionamento nem limites.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- Protocolo: `Request.CardImport(actor, project: String? = null, id: String? = null, query: String? = null, proof: String? = null)` com `cmd` `card.import`; exatamente um entre `id` e `query`. Resposta: `Response.CardsResult` (um `CardView` por card criado ou reimportado).
- Core: `EventType.CARD_REIMPORTED` (payload `provider`, `externalId`, `changed` = `true|false`, `title` quando mudou). Criação reaproveita `CARD_CREATED` com payload `origin=imported`, `provider`, `externalId`, como o `origin=rebuilt` do rebuild.
- Daemon: `CardImport(store, recorder, registry, trackerOf: (Project) -> Tracker, ownerProof, clock, log)`, mesmo molde do `CardRebuild` (`@Synchronized`, dono + prova, escolha do projeto); `ProjectDelivery.trackerOf(project, tmpDir)` devolve `GhTracker` do slug do origin, ou um Tracker que falha toda chamada.

**Camadas e arquivos**
- `core`: `core/src/main/kotlin/sfv2/core/Event.kt` (novo tipo).
- `daemon`: `CardImport.kt` (novo), `Protocol.kt` (request, parse), `SocketServer.kt` (despacho), `CardCommands.kt` (ramo `when` exaustivo), `ProjectDelivery.kt` (`trackerOf`), `Main.kt` (fiação).

**Comportamento**
- `id`: `tracker.fetch(id)`; null → erro com o id. Sem card com a ref `(provider, externalId)` → `Card.create(title, INBOX).withReference(ExternalRef(provider, id, url, now, TextHash(item.textHash)))`, evento `CARD_CREATED` com ator `Actor.Adapter(provider)`, `store.assignProject`. Com card existente → reimporta: título e referência novos (reconstrói a ref trocando-a na lista, mantendo coluna e demais refs), evento `CARD_REIMPORTED` sempre, `changed=false` e card intacto quando o hash é igual.
- `query`: `tracker.search(query)`; cada item sem card existente vira card como acima; itens já importados são pulados sem evento. Falha do tracker (`TrackerException`) antes de criar qualquer card encerra com erro; mensagem só com operação e id, nunca texto da issue.
- O corpo da issue não é guardado no card (o `Card` só tem título): entra no hash da referência; guardá-lo é decisão da porta de texto externo (#57).
- Provider fixo `github` (único tracker existente). Busca de duplicata por varredura de `store.cards()` sob `@Synchronized`, só entre os cards do projeto escolhido (`store.projectOf(id) == project.repo` para o projeto escolhido; o número da issue só é único dentro do repositório). O título do card nasce de `normalizeTitle` (AC-10), pura e testável. A reimportação atualiza o título só nas colunas Inbox, Ready for Refinement, Refinement, Spec Review e Ready for Dev (AC-11); a busca pula `TrackerItemState.CLOSED` (AC-12).
- Logs `key=value` só com ids; sem migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-9 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-10 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-11 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-12 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Teste em `CardImportTest.kt` novo: nenhuma suíte do daemon cobre importação; o molde é o `CardRebuildTest`, que cobre outro comando.
- CLI `sfv2 import` fica fora: o card é do daemon e a CLI adicionaria uma camada; vira card próprio.
- Novo `EventType.CARD_REIMPORTED` no core: nenhum tipo existente descreve a reimportação sem mentir; por isso o card toca 2 camadas e pesa `heavy`.
- Busca pula cards já importados sem atualizar o texto; só a reimportação por id atualiza (a issue pede "reimportar" como comando explícito).
- Pedido não vindo do dono é recusado pela prova de dono, como no `card.rebuild`.
- Bloqueio por #53: já fechada, nada a fazer.

## Open questions
- none

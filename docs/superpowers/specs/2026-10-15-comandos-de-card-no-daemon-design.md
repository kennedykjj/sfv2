# Comandos de card no daemon — spec

**Issue:** #16 · **Branch:** `feat/16-comandos-de-card-no-daemon` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
**Problema:** CLI, app e agentes precisam de um ponto único que aplique as regras do core e grave pelo store; hoje o daemon não expõe comandos de card.
**Público:** o desenvolvedor sozinho com muitos projetos (e, por extensão, o desenvolvedor numa empresa que exige trilha de auditoria).

**Critérios de aceitação**
- AC-1: Dado o comando de criar card, quando o daemon o processa, então o card fica gravado no banco e um evento de criação com ator dono é gravado na mesma operação.
- AC-2: Dado um movimento que as regras do core recusam, quando o daemon o processa, então nenhum card nem evento é alterado ou gravado.
- AC-3: Dado um movimento recusado, quando o daemon responde, então a resposta traz o motivo da recusa.
- AC-4: Dado cards gravados no banco, quando o cliente pede listar, então o daemon devolve os cards conforme o estado atual do banco.
- AC-5: Dado um card gravado, quando o cliente pede mostrar com o id dele, então o daemon devolve o estado atual desse card gravado no banco.

**Fora de escopo:** comandos de projeto, execução de agentes e gates, CLI/app/MCP (clientes dos comandos), filtros e paginação além do necessário para listar, importação de tracker externo.

**Aderência ao produto:** serve "o daemon é o único escritor do estado" e "toda ação é um evento com ator"; não contradiz posicionamento nem escopo excluído.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos (protocolo JSON por linha, `daemon/.../Protocol.kt`):**
- `{"cmd":"card.create","actor":"owner","title":"..."}` → `{"ok":true,"result":{"id","title","column"}}`; card nasce em INBOX, grava `CARD_CREATED` (payload `title`).
- `{"cmd":"card.move","actor":"...","id":"...","to":"<COLUMN>"}` → ok com o card, grava `CARD_MOVED` (payload `from`, `to`); recusa de `MoveRules.check` → `{"ok":false,"error":"<motivo>"}` sem escrever.
- `{"cmd":"card.list"}` → `{"ok":true,"result":[{"id","title","column"}]}`; `{"cmd":"card.show","id":"..."}` → card ou erro `card not found: <id>`.
- `actor` aceita `owner`, `agent:<role>`, `factory:<component>`; o socket 0600 é a fronteira de confiança.

**Camadas e arquivos:**
- `store` — `store/src/main/kotlin/sfv2/store/CardEventStore.kt`: nova `cards(): List<StoredCard>` (`StoredCard(id, card)`); `store/src/main/sqldelight/sfv2/store/db/Schema.sq`: query `allCards` (ordem por rowid).
- `daemon` — `Protocol.kt` (novos `Request`/`Response` e render), novo `CardCommands.kt` (aplica `MoveRules`, gera id e `EventId` UUID, recebe `Clock` e `CardEventStore`), `SocketServer.kt` (recebe `CardCommands` e despacha), `Main.kt` (abre `Database` do caminho de dados e injeta).
- `core` não muda; `MoveRules`, `Card`, `Event` são reusados.

**Migração:** nenhuma (schema inalterado; só query nova).

## Test plan
_Tech Lead_

Comandos testados em processo pelo `SocketServer` com `Database` temporário e `Clock` fixo; sem mocks.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Revisão do dono interino_
- O ator é declarado pelo cliente e o socket 0600 não distingue o dono de um agente rodando como o mesmo usuário. Aceito só na fase 1, em que nenhum agente fala com o daemon. Antes de qualquer agente ter caminho até o socket (MCP, #28/#29), o issue de follow-up exige que o ator dono venha de prova do dono, não de texto do pedido (ARCHITECTURE, invariante 4).
- Nunca logar `title` nem payload: só id do card e tipo do comando.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca 2 camadas (daemon e store), pela regra de peso.
- Ator vem no campo `actor` da requisição: o daemon não autentica, o socket 0600 é a fronteira; sem isso AC-1 ("ator dono") e `MoveRules` não têm de onde ler o ator.
- `card.list` precisa de query nova no store (`cards()`); AC-4 é verificado no store e o fluxo ponta a ponta de listar entra no mesmo teste de socket do AC-5.
- Id do card é UUID gerado no daemon (o core não tem gerador de id e deve ficar puro).
- Testes reaproveitam `SocketServerTest.kt` e `ModuleTest.kt` do store; nenhum arquivo de teste novo.

## Open questions
- none

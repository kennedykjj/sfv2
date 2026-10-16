# MCP: amarrar sessão ao token, revogar tokens e validar entradas — spec

**Issue:** #142 · **Branch:** `feat/142-mcp-sessao-token-revogar-validar` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o servidor MCP de leitura (#28) aceita qualquer token válido para qualquer sessão HTTP aberta, nunca expira nem revoga tokens, loga o `cardId` do agente sem validar e devolve ao agente a mensagem original de erros de infraestrutura (caminhos locais). Isso precisa fechar antes de o MCP receber ferramentas de pedido (#29).
**Audiência:** o desenvolvedor sozinho com muitos projetos (até dez numa máquina), cujos agentes de projetos diferentes não podem alcançar o servidor um do outro.
**Critérios de aceitação**
- **AC-1** — Dado um `Mcp-Session-Id` aberto com o token do projeto A, quando chega um pedido com esse id e o token do projeto B, então é recusado e a sessão de A segue intacta.
- **AC-2** — Dado `McpSessions.close(token)` e um prazo de vida configurável (relógio injetado), quando o token é revogado ou expira, então qualquer pedido com ele é recusado e as sessões HTTP dele são encerradas.
- **AC-3** — Dado um `cardId` que não casa com `[A-Za-z0-9-]{1,64}`, quando uma ferramenta é chamada, então é recusado antes de qualquer consulta e nenhum log contém o valor recebido.
- **AC-4** — Dado um erro de infraestrutura numa ferramenta, quando o agente recebe a resposta, então vê a mensagem fixa "tool failed" com um código e o detalhe vai só para o log do daemon, sem texto do agente.
**Fora de escopo:** ferramentas de escrita (#29); mudar o formato de token ou a autenticação além de amarrar, revogar e expirar.
**Adequação ao produto:** serve "texto externo é dado não confiável" e o isolamento entre projetos na mesma máquina; não contradiz o posicionamento nem o que está fora de escopo.
**Toca UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Camadas:** só `daemon`.

**Contratos** (pacote `sfv2.daemon`, sem mudança de evento, store ou `core`):
- `McpSessions(clock: Clock = Clock.systemUTC(), ttl: Duration = DEFAULT_TTL)`: cada token guarda `expiresAt = clock.instant() + ttl`. `find(token)` devolve `null` se o token expirou (e o remove). Novo `close(token: String)`: remove o token e encerra as sessões HTTP associadas. Novo `bind(httpSessionId: String, token: String)` e `owns(httpSessionId: String, token: String): Boolean`: o id de sessão HTTP fica ligado ao digest do token que o abriu.
- `McpRequestGuard.authorize(...)` ganha o parâmetro `sessionId: String?` (cabeçalho `Mcp-Session-Id`): se vier e estiver ligado a outro token, devolve `null` (a sessão original não é tocada). Sessão HTTP desconhecida ou encerrada também é recusada.
- `McpServerFactory`: o filtro lê `Mcp-Session-Id`; a ligação id→token é feita quando o SDK abre a sessão (resposta do `initialize`). `close(token)` e a expiração fecham o transporte das sessões HTTP do token (o engenheiro escolhe o gancho do SDK; se o SDK não expõe o fechamento, o filtro recusar o id e o `McpSessions` esquecer o id satisfaz o AC-2).
- `McpReadTools`/`McpServerFactory`: `cardId` validado por `Regex("[A-Za-z0-9-]{1,64}")` (`matches` completo) antes de `requireOwnCard` ou de qualquer consulta; recusa vira `McpToolException("argument cardId is invalid")` sem o valor, e o log `mcp_refused` leva `reason=invalid_card_id` e nunca o valor recebido.
- `toolResult` captura `McpToolException` (mensagem ao agente, como hoje) e qualquer outra `RuntimeException`: devolve `isError = true` com texto fixo `tool failed (code=<código>)` (código = `internal`), e registra no log do daemon `mcp_tool_failed tool=<nome> code=internal error=<classe da exceção>`, sem mensagem original nem texto vindo do agente. `McpToolException` não pode mais carregar o `cardId` cru do agente em mensagens ao agente além do já validado.

**Arquivos:** `daemon/src/main/kotlin/sfv2/daemon/McpSession.kt`, `McpServerFactory.kt`, `McpReadTools.kt`; testes nos existentes `McpServerFactoryTest.kt` e `McpReadToolsTest.kt`.

**Migração:** nenhuma; o TTL tem padrão (`DEFAULT_TTL`, 24 h) e o construtor atual de `McpSessions()` segue válido.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |

## Security
_Cybersec_
- **Amarração:** `owns(sessionId, token)` compara o digest do token que abriu a sessão com o do pedido, em tempo constante (`MessageDigest.isEqual`). Id desconhecido, encerrado ou de outro token dá o mesmo 401 sem corpo, sem confirmar ids ao atacante; a sessão de A não é tocada. Guardar só digests, nunca o token em claro.
- **Ordem do guard:** origin, host, bearer, `find`, `owns`. O log `mcp_refused` usa só `reason` de lista fechada: sem token, sem `Mcp-Session-Id`, sem `Authorization`.
- **Expiração e revogação:** `expiresAt` fixado na abertura (sem renovar por uso), com `Clock`/`Duration` injetados; `ttl <= 0` falha na construção (`require`). `close(token)` remove o token e todos os ids dele; se o SDK não fechar o transporte, o filtro recusa o id.
- **`cardId`:** `Regex("[A-Za-z0-9-]{1,64}").matches` sobre o valor inteiro (nunca `find`), antes de `requireOwnCard`, do store e dos arquivos; nulo ou tipo errado seguem o mesmo caminho. Mensagem fixa `argument cardId is invalid`; o log leva só `reason=invalid_card_id`.
- **Erros:** `RuntimeException` não de domínio vira `tool failed (code=internal)`; o log leva `tool`, `code` e a classe da exceção, sem `message`, sem stack trace e sem argumentos do agente. `Error` (OOM etc.) não é capturado. Revisar as `McpToolException` existentes para que nenhuma interpole texto do agente ou caminho.
- Testes: id de A com token B recusado e A segue ativo; id inexistente/encerrado com resposta idêntica; TTL em `ttl-1` vale, em `ttl` recusa; `close` não afeta o token de outro projeto; `cardId` `../x`, `a/b`, `a\nb`, `a b`, vazio, 65 caracteres, Unicode, `abc\n` recusados sem chamar o store e sem o valor no log; exceção com caminho local não aparece na resposta nem no log.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Sem teste novo: `McpServerFactoryTest.kt` e `McpReadToolsTest.kt` já cobrem o servidor e as ferramentas.
- TTL padrão de 24 h com construtor sem argumentos preservado: o chamador atual não muda e o prazo é configurável por parâmetro.
- O gancho do SDK para ligar o id de sessão ao token e fechar o transporte fica a cargo do engenheiro; o filtro recusar o id basta para o AC-2.
- Código de erro único `internal`: não há outras classes de falha de infraestrutura hoje; códigos novos entram quando houver necessidade.
- Peso `heavy`: a regra da fábrica o exige por haver seção `## Security` preenchida (a issue previa standard); o dono pode baixar em Spec Review.

## Open questions
- none

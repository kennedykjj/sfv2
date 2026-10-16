# MCP: ferramentas de pedido — spec

**Issue:** #29 · **Branch:** `feat/29-mcp-ferramentas-de-pedido` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o agente precisa informar que terminou e pedir escritas, mas não pode decidir sozinho: ele pede, o daemon decide. Sem essa fronteira, o relato de um agente moveria cards ou gravaria fora do que a fábrica controla.

**Público:** o desenvolvedor sozinho com muitos projetos (e, por extensão, o que trabalha em empresa com trilha de auditoria): confia que a fábrica produz sem que o agente passe dos limites.

**Critérios de aceitação**
- AC-1: Dado um agente com sessão MCP válida de um card, quando ele relata conclusão, então o daemon registra um evento com ator agente e seu papel.
- AC-2: Dado o relato de conclusão do AC-1, quando o evento é registrado, então a coluna do card permanece a mesma (só gate verde move o card).
- AC-3: Dado um pedido de escrita de arquivo dentro do worktree do card, quando o agente o envia, então o daemon o aceita e a escrita é feita.
- AC-4: Dado um pedido de escrita fora do worktree do card (caminho absoluto externo ou `..`), quando o agente o envia, então é recusado com motivo e nada é escrito.
- AC-5: Dada qualquer ferramenta de pedido, quando ela altera estado, então a gravação passa pelas regras do core; nenhuma grava direto no banco.
- AC-6: Dada uma sessão ligada a um projeto, quando o agente pede algo de card de outro projeto, então é recusado.

**Fora de escopo:** mover card por relato do agente; ferramentas de leitura (já existem em `McpReadTools`); aprovação do dono; qualquer chamada a sistemas externos.

**Encaixe no produto:** serve "gates são scripts, não a palavra do agente" e "toda ação é um evento com ator". Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos (ferramentas MCP novas, mesmo servidor das de leitura):**
- `completion_report(cardId, summary)` → `{ eventId }`. Grava o evento `AGENT_REPORTED` com ator `Agent(role)`; payload `summary` (limitado a 4 KB) e `column` atual. Não move o card.
- `file_write_request(cardId, path, content)` → `{ path, bytes }`. Recusa se o caminho resolvido sai do worktree do card; senão o daemon grava o arquivo e registra `AGENT_WRITE_REQUESTED` (payload: `path`, `bytes`, sem o conteúdo).
- `McpSessions.open(repo, role)` e `McpSession(repo, role)`: o papel do agente vem da sessão (aberta pelo daemon ao lançar o run), nunca de argumento da ferramenta.

**Camadas e arquivos:**
- `core` — `core/src/main/kotlin/sfv2/core/AgentRequests.kt` (novo): funções puras `reportCompletion(card, role, summary, id, at): Event` e `requestWrite(card, role, path, bytes, id, at): Event` com as regras (card em coluna de trabalho de agente: Refinement, Coding, Validation; resumo não vazio e limitado; caminho relativo e não vazio). Retornam o `Event` ou lançam `AgentRequestRefusedException`. `core/.../Event.kt`: novos `EventType.AGENT_REPORTED` e `AGENT_WRITE_REQUESTED`.
- `daemon` — `daemon/.../McpRequestTools.kt` (novo): resolve o card da sessão (mesma checagem de projeto de `McpReadTools`), chama o core, grava via `EventRecorder.record` (único escritor; o card em si não muda) e, para escrita, resolve o worktree (`WorktreeManager`/`CardWorktree.path`), normaliza o caminho, rejeita absoluto, `..` e symlink que escapa (`toRealPath` do pai), grava o arquivo e só então registra o evento. `McpServerFactory.kt`: registra as duas ferramentas. `McpSession.kt`: papel na sessão.
- Nenhuma ferramenta acessa `CardEventStore` para escrever: só `EventRecorder`, após a decisão do core.

**Migração:** nenhuma de esquema; os eventos são JSON com `type` em texto. Aditivo (expand): leitores antigos falham em tipo desconhecido (`UnknownEventTypeException`), então o daemon e o core sobem juntos.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |
| AC-5 | core | `core/src/test/kotlin/sfv2/core/AgentRequestsTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpReadToolsTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca duas camadas (core e daemon), pela regra de peso; a issue previa standard.
- O papel do agente vem da sessão e não de argumento: o agente não pode se passar por outro papel.
- O conteúdo escrito não vai no payload do evento, só caminho e tamanho: evita logar texto de spec/código.
- Arquivo novo `AgentRequestsTest.kt` no core: nenhuma suíte existente cobre regras de pedido de agente.
- `McpReadToolsTest.kt` cobre as ferramentas de card do daemon (classe `McpRequestTools` segue o mesmo padrão); `McpServerFactoryTest.kt` cobre o fluxo ponta a ponta pelo MCP.
- Reutilizo a checagem de projeto da sessão (AC-6) em vez de criar outra: mesma fronteira de confiança.

## Open questions
- none

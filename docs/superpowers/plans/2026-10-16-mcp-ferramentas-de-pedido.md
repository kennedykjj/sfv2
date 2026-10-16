# MCP: ferramentas de pedido — plano (#29)

Spec: `docs/superpowers/specs/2026-10-16-mcp-ferramentas-de-pedido-design.md`. Peso `heavy`: duas camadas, ordem core → daemon, cada tarefa com o agente `engineer`.

## Tarefa 1 — core (engineer)

Arquivos: `core/src/main/kotlin/sfv2/core/Event.kt`, `core/src/main/kotlin/sfv2/core/AgentRequests.kt` (novo). Nada depende de outra tarefa.

- `EventType.AGENT_REPORTED` e `AGENT_WRITE_REQUESTED` (conferir `EventJson.kt`).
- `class AgentRequestRefusedException(message): RuntimeException` (mensagem com o id do card).
- `object AgentRequests` com funções puras, sem IO nem relógio:
  - `reportCompletion(cardId: String, card: Card, role: String, summary: String, id: EventId, at: Instant): Event`: card em REFINEMENT, CODING ou VALIDATION (senão recusa); resumo não vazio após trim e até 4096 bytes UTF-8; ator `Actor.Agent(role)`, tipo `AGENT_REPORTED`, payload `summary` e `column` (nome do enum).
  - `requestWrite(cardId: String, card: Card, role: String, path: String, bytes: Int, id: EventId, at: Instant): Event`: mesma regra de coluna; caminho relativo, não vazio, sem caractere de controle; payload `path`, `bytes`, `column` (sem conteúdo); tipo `AGENT_WRITE_REQUESTED`.
- O card devolvido ao gravar é o mesmo: o core nunca move coluna aqui (AC-2, AC-5).

## Tarefa 2 — daemon (engineer, depende da 1)

Arquivos: `McpSession.kt`, `McpRequestTools.kt` (novo), `McpServerFactory.kt`, `WorktreeManager.kt`; ajustar os call sites dos testes existentes que usam `McpSession(repo)` e `McpSessions.open(repo)` (feito pelo QA no commit de testes).

- `McpSession(repo, role, cardId)`; `McpSessions.open(repo, role, cardId): String`.
- `WorktreeManager.existing(project, cardId): Path?`: caminho do worktree já criado (`directoryFor`), `null` se o diretório não existe; nunca cria.
- `McpRequestTools(session, store, recorder: EventRecorder, worktrees: WorktreeManager, registry, clock: Clock, log: DaemonLog)`:
  - `completionReport(cardId, summary): ReportedView(eventId)`; `fileWriteRequest(cardId, path, content): WriteView(path, bytes)`.
  - Recusa (`McpToolException`) se `cardId` for inválido, de outro projeto (AC-6) ou diferente de `session.cardId` (AC-9).
  - Escrita: conteúdo ≤ 256 KiB, caminho ≤ 512 caracteres; normaliza; recusa absoluto, `..`, segmento `.git`, prefixos `.shipfloor`, `.github`, `.claude`, `CLAUDE.md` da raiz e `forbidden` do projeto (mesma regra do `ScopeGate`; extrair a regra para reuso em vez de duplicar); recusa se o arquivo final ou algum pai for link simbólico ou se `toRealPath` do pai sair do worktree; worktree ausente = recusa. Grava atômico (temporário no mesmo diretório + `move`, sem seguir links), só depois registra o evento via `EventRecorder.record` (card inalterado). Log com card e caminho, nunca conteúdo.
  - Relato: `EventRecorder.record` com o evento de `AgentRequests.reportCompletion`.
- `McpServerFactory`: recebe `recorder`, `worktrees`, `clock`; registra `completion_report(cardId, summary)` e `file_write_request(cardId, path, content)` com schema de string; papel só da sessão.

## Ordem e gates

Tarefa 1 → gates de escopo `core`; tarefa 2 → gates `daemon`; `green` por seletor dos testes de aceitação.

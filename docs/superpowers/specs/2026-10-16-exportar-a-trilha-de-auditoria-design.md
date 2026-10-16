# Exportar a trilha de auditoria — spec

**Issue:** #89 · **Branch:** `feat/89-exportar-a-trilha-de-auditoria` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O desenvolvedor numa empresa precisa provar quem aprovou o quê, quando e a que custo; hoje os eventos de um card existem só dentro da fábrica e não saem em formato que um auditor ou o Jira consiga usar.
**Audience:** O desenvolvedor dentro de uma empresa (trilha de auditoria).
**Acceptance criteria**
- **AC-1** — Dado um card com eventos, quando rodo `sfv2 audit export <id> --format json`, então recebo um documento JSON com um registro por evento, em ordem cronológica, contendo id, momento, tipo e ator (`owner`, `agent:<role>` ou `factory:<comp>`).
- **AC-2** — Dado o mesmo card, quando rodo `sfv2 audit export <id> --format csv`, então recebo um CSV com cabeçalho e as mesmas linhas e colunas do JSON.
- **AC-3** — Dado um card com eventos de custo, quando exporto em qualquer formato, então cada evento de custo traz o valor em USD, o status (KNOWN, PARTIAL ou UNKNOWN) e a indicação de que é estimativa; custo UNKNOWN sai vazio, nunca zero.
- **AC-4** — Dado um evento de aprovação do dono, quando exporto, então ele aparece com ator `owner` e o momento da decisão.
- **AC-5** — Dado um CSV cujo texto de uma célula começa com `=`, `+`, `-` ou `@`, quando exporto, então a célula é neutralizada como no `report.csv` existente.
- **AC-6** — Dado um id de card inexistente ou um `--format` inválido, quando rodo o comando, então ele falha com código de saída diferente de zero e mensagem que diz o que houve, sem saída parcial.
- **AC-7** — Dado um card, quando exporto, então o estado do card e seus eventos não mudam (somente leitura) e a exportação funciona com o app fechado.
**Out of scope:** Exportação de vários cards ou do projeto inteiro; filtros por período ou ator; assinatura criptográfica do arquivo; envio para Jira ou outro sistema externo; UI no app; incluir texto de issue ou spec no arquivo (só ids e metadados).
**Product fit:** Serve "toda ação é um evento com ator" e "custo é estimativa, sempre rotulada". O arquivo fica na máquina, sem sincronizar com ferramenta externa, então não contradiz o posicionamento.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- Daemon, comando read-only novo `audit.export` com `{"cmd":"audit.export","id":"<card>","format":"json"|"csv"}`; resposta `{"ok":true,"text":"<documento>"}`. Id desconhecido ou `format` inválido → `Response.Error` com o id na mensagem. Não exige `proof` (leitura, como `card.show` e `report.csv`).
- Colunas (CSV e chaves do JSON, na mesma ordem): `event_id`, `at`, `type`, `actor`, `cost_usd_estimate`, `cost_status`. JSON: `{"card":"<id>","events":[...]}`. Custo só nos eventos `RUN_COST_RECORDED` (lido de `cost_usd` e `status` do payload); nos demais eventos as duas colunas saem vazias; `UNKNOWN` sai com `cost_usd_estimate` vazio. O resto do payload não é exportado (pode carregar texto externo).
- CLI: `sfv2 audit export <id> --format json|csv` (default `json`) imprime o `text` retornado.

**Camadas e arquivos**
- `daemon`: `AuditExport.kt` (novo; função pura `AuditExport.render(cardId, events, format)` com neutralização de fórmulas igual à do `CardReport`), `Protocol.kt` (`Request.AuditExport`, parse, `Response.AuditResult`, render), `CardCommands.kt` (handler), `SocketServer.kt` (roteamento).
- `cli`: `AuditCommands.kt` (novo; grupo `audit` e `export`), `CardCommands.kt` (registro em `withCardCommands`).
- Sem mudança em `core`, `store` (usa `store.events(id)`), `ports`, `adapters` nem `app`. Sem migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-6 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Exportação por card, não por projeto: é o que o critério da issue pede; o resto fica fora de escopo.
- Sem texto de issue/spec no arquivo: texto externo é não confiável e não deve vazar para a trilha.
- A issue diz camada `cli`/light, mas ler eventos de um card exige comando novo no daemon (a CLI não tem acesso ao banco; `events.subscribe` é stream sem fim): duas camadas, peso `heavy` pela regra.
- Formatação no daemon, CLI fina: segue o precedente do `report.csv`, e o teste fica em processo no `ProtocolTest`.
- Sem arquivos de teste novos: `ProtocolTest.kt` e `MainTest.kt` já cobrem o protocolo e a CLI.
- A issue lista "Bloqueada por #17" (fechada) enquanto o roadmap cita #62 (aberta, à mão): segui a relação do GitHub e não bloqueei.

## Open questions
- none

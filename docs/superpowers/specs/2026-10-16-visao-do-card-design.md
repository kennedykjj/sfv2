# Visão do card — spec

**Issue:** #66 · **Branch:** `feat/66-visao-do-card` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O dono não consegue, no app, abrir um card e ver o que a fábrica fez nele: qual a spec, quem fez o quê e quando, e quanto cada run custou. Sem isso, aprovar a spec ou liberar a entrega é decidir às cegas.
**Audience:** O desenvolvedor dono da fábrica (sozinho com muitos projetos, ou dentro de empresa que precisa de trilha de auditoria).
**Acceptance criteria**
- **AC-1** — Dado um card existente, quando o dono o abre no app, então vê o título, a coluna atual e a spec do card.
- **AC-2** — Dado um card com eventos, quando a visão abre, então a linha do tempo lista cada evento em ordem cronológica com ator, tipo e instante.
- **AC-3** — Dado um evento de aprovação do dono, quando aparece na linha do tempo, então é identificado como aprovação assinada pelo dono, distinto de eventos de agentes.
- **AC-4** — Dado um card com runs, quando a visão abre, então cada run mostra seu custo medido das transcrições, rotulado como estimativa.
- **AC-5** — Dado um card com runs, quando a visão abre, então o custo total do card é exibido e é a soma dos custos dos runs.
- **AC-6** — Dado um card sem spec, sem eventos ou sem runs, quando a visão abre, então a seção vazia mostra um estado vazio explícito, sem erro.
- **AC-7** — Dado que o daemon está indisponível, quando o dono abre a visão do card, então vê o estado de desconexão do app, sem dados inventados.
- **AC-8** — Dado um card que recebe novo evento ou run com a visão aberta, quando o daemon o registra, então a visão reflete o dado novo sem reabrir.
**Out of scope:** Editar a spec, aprovar ou liberar a entrega pela visão (ações do dono ficam em outro item); mover o card; visão agregada de custo por projeto; exibir texto de transcrição dos runs; exportar a trilha.
**Product fit:** Serve "toda ação é um evento com ator" e "custo medido das transcrições, rotulado como estimativa". Não contradiz o posicionamento nem o que está fora de escopo: é leitura do estado que o daemon possui.
**Touches UI:** yes

## UX
_UX/UI Designer_

- **Fluxo:** o dono abre o card (seleção na lista/esteira do app) e a visão ocupa a janela, uma coluna rolável: cabeçalho, Spec, Linha do tempo, Runs. Voltar e Esc fecham e devolvem o foco ao item de origem. Só leitura: nenhum botão de ação (fora de escopo).
- **Cabeçalho:** `#n` em mono (cor `text`, não accent: o accent é só para o que espera o dono), título em 24px/600 (Section), coluna em Meta 13px `muted`, e à direita o custo total em Headline 17px `text`: `US$ 1,36 est.` (Estimate Rule; `US$ ?` com a explicação "falta preço de um modelo" quando faltar preço, nunca zero).
- **Seções:** cada uma é um painel (`panel`, borda `edge`, raio 16px, padding 16/24) com título Headline 17px; texto da spec em Body 14px, `soft`, máx. 64ch, em prosa sem renderizar HTML. Reaproveitar `Tokens`/`Texts` de `ConnectionScreen.kt` (mover para arquivo comum) sem componente novo além de um `Panel` e de linhas.
- **Linha do tempo:** lista em ordem cronológica (mais antigo primeiro). Cada linha: instante em mono 12px `faint` (`16 out 14:32`), marca quadrada/ponto, ator em 600, tipo em `soft`. Evento de aprovação do dono: marca cheia `text` e tag `assinada pelo dono` (componente `tag`, 13px); eventos de agentes têm marca vazada `muted` e sem tag, assim o estado não depende só de cor.
- **Runs:** tabela simples alinhada à direita nos números (tabular-nums): início, estágio, modelo, duração, custo `US$ 0,42 est.`; linha final "Total" = soma, em `text` 600. Sem texto de transcrição.
- **Estados vazios (um por seção, linha `faint` 14px):** `Este card ainda não tem spec.` · `Nenhum evento registrado.` · `Nenhum run ainda.` (custo total no cabeçalho: `US$ 0,00 est.` só quando há zero runs medidos; sem preço, `US$ ?`).
- **Carregando:** cabeçalho com `#n` e sem skeleton, seções com `Lendo o card…` (`muted`); sem animação nova.
- **Erro / daemon indisponível (AC-7):** reutilizar `ConnectionScreen` (estado Desconectado e explicação) no lugar da visão, sem dados antigos nem inventados; ao reconectar a visão volta sozinha. Card inexistente: `Card #n não encontrado.` com botão ghost `Voltar`.
- **Tempo real (AC-8):** novos eventos/runs entram sem reabrir; a posição de rolagem e o foco são preservados, e o total é recalculado. Região `liveRegion = Polite` só no custo total e na contagem de eventos, sem anunciar cada linha.
- **Janela pequena (< 860dp):** painéis em largura total com padding 16dp; a tabela de runs vira cartões empilhados (rótulo `muted` 12px acima do valor); linha do tempo quebra ator/tipo abaixo do instante. Alvos ≥ 24dp.
- **Acessibilidade:** contraste dos tokens do DESIGN.md (Charcoal), foco com anel 2px `sand`, nomes de seção como títulos semânticos; sem emoji, sem cor nova, sem sombra em repouso.
- **Textos novos (pt-BR, único idioma hoje):** `Spec`, `Linha do tempo`, `Runs`, `Total`, `assinada pelo dono`, `est.`, `Lendo o card…`, `Card #n não encontrado.`, `Voltar`, mais os vazios acima.

## Architecture
_Tech Lead_

**Contrato novo (daemon):** `{"cmd":"card.detail","id":"<id>"}` → `{"ok":true,"detail":{"card":{id,title,column},"spec":{"status":"valid|missing","text":"..."},"events":[{id,at,actor,type,payload}],"runs":[{run_id,at,stage,runtime,status,cost_usd,estimate}],"total":{"status":"KNOWN|PARTIAL|UNKNOWN","cost_usd":"..."}}}`; card inexistente → `{"ok":false,"error":"card not found: <id>"}` (igual a `card.show`). `card.show` não muda. Eventos usam o mesmo formato de `renderEvent` (ator `owner`, `agent:<role>`, `factory:<comp>`). Runs são derivados dos eventos `RUN_COST_RECORDED` (payload já tem `run_id`, `stage`, `runtime`, `status`, `cost_usd`, `estimate`); o total soma os `cost_usd` e vira `PARTIAL` se algum run for `PARTIAL`/`UNKNOWN`.

**Camada daemon:**
- `daemon/.../Protocol.kt`: `Request.CardDetail(id)`, `Response.CardDetailResult(...)`, parse de `card.detail`, render da resposta; `SocketServer.kt` roteia o novo pedido como leitura (sem prova do dono).
- `daemon/.../CardDetail.kt` (novo): função pura que monta o detalhe a partir de `Card`, `SpecRead` e `List<Event>`, sem IO.
- `daemon/.../CardCommands.kt`: recebe `SpecStore` e atende `CardDetail`; `Module.kt` passa o `SpecStore` já existente.

**Camada app:**
- `app/.../DaemonClient.kt` (novo): `fun interface CardDetailSource` com `fetch(id)` e `SocketCardDetailSource` (socket Unix, mesmo padrão do `SocketDaemonProbe`); parse do JSON com `kotlinx-serialization-json` (entra em `app/build.gradle.kts` via `libs.kotlinx.serialization.json`, versão já fixada no catálogo; plugin de serialização não é necessário, só `Json.parseToJsonElement`).
- `app/.../CardDetailModel.kt` (novo): estado `Loading | Loaded(detail) | NotFound | Disconnected` como sealed type; atualização ao vir evento do `events.subscribe` para o mesmo card (refaz o `card.detail`, AC-8), usando `Clock`/`Flow` injetados, sem relógio real. Formatação de custo e de instante em funções puras.
- `app/.../CardDetailScreen.kt` (novo, Compose): cabeçalho, Spec, Linha do tempo, Runs conforme UX; `ConnectionScreen` quando `Disconnected` (AC-7). `Tokens`/`Texts` de `ConnectionScreen.kt` passam para `Theme.kt` (arquivo comum).
- Não mexe em `core`, `store`, `ports`, `adapters`, `cli`. Sem migração e sem mudança de esquema.

**Arquivos (11):** Protocol.kt, SocketServer.kt, CardDetail.kt, CardCommands.kt, Module.kt (daemon); DaemonClient.kt, CardDetailModel.kt, CardDetailScreen.kt, Theme.kt, ConnectionScreen.kt, app/build.gradle.kts (app). Dono de cada: `daemon` ou `app`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-8 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Os dados da visão vêm de um comando novo `card.detail` em vez de ampliar `card.show`: `card.show` já tem clientes (CLI, testes) e mudar sua resposta seria quebra de contrato.
- Runs vêm dos eventos `RUN_COST_RECORDED` já gravados, sem tabela nova: o evento é a fonte de verdade e já traz estágio, runtime e custo.
- A tabela de runs mostra início, estágio, runtime e custo: o UX pediu modelo e duração, mas o evento não grava duração e o modelo só existe dentro de `tokens`; ficam fora até haver dado (sem inventar).
- AC-8 usa `events.subscribe` e refaz `card.detail` ao chegar evento do card: reaproveita o canal que já existe e evita polling.
- Novo `CardDetailModelTest.kt` no app: nenhuma suíte de teste do módulo cobre o modelo da visão (a dos testes do app é de conexão e skins).
- Os ACs 1–6 são testados na camada daemon (conteúdo da resposta); a renderização Compose é verificada na validação (captura desktop e mobile), não por teste automatizado.
- App só em pt-BR com `Texts` sem i18n, como o UX recomendou: o app inteiro hoje é pt-BR.
- Peso `heavy`: toca 2 camadas (daemon e app).

## Open questions
- none

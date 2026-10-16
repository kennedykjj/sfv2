# Feed com atores, custo por evento e histórico por estágio — spec

**Issue:** #240 · **Branch:** `feat/240-feed-com-atores-custo-por-evento-e-historico-por-estagio` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** O feed do dia mostra o ator como texto cru (`agent:dev`, `factory:cost`), não mostra quanto cada execução custou e não deixa ver o dia por estágio. Para saber "o que o agente fez no Develop e quanto custou" o dono precisa abrir o card ou ler o painel de custo e cruzar na cabeça.
**Audience:** O desenvolvedor sozinho com muitos projetos, que confere a fábrica em olhadas rápidas (e quem precisa da trilha de quem fez o quê e a que custo).
**Acceptance criteria**
- **AC-1** — Dado um evento no feed, quando a linha é exibida, então ela mostra a espécie do ator em português (dono, agente, fábrica, adaptador) e o detalhe quando o ator o tem (ex.: `agente · dev`, `fábrica · cost`); um ator de espécie desconhecida aparece com o texto original, sem erro.
- **AC-2** — Dado um evento `RUN_COST_RECORDED`, quando a linha é exibida, então ela traz o custo daquele evento rotulado como estimativa: `US$ 0,42 est.` para KNOWN, `≥ US$ 0,42 est.` para PARTIAL e `US$ ?` para UNKNOWN; eventos de outros tipos não mostram custo.
- **AC-3** — Dado um `RUN_COST_RECORDED` com `cost_usd` ausente ou ilegível, quando a linha é exibida, então ela mostra `US$ ?` e nunca zero.
- **AC-4** — Dado o feed do dia, quando há eventos com estágio (`payload.stage`), então o feed oferece o filtro "Todos" mais um por estágio presente no dia, cada um com a contagem de eventos; "Todos" é o padrão e a contagem de "Todos" é o total do dia.
- **AC-5** — Dado um estágio escolhido, quando a lista é exibida, então ela mostra só os eventos desse estágio, em ordem cronológica, e um evento novo do mesmo estágio entra sem recarregar; um evento de outro estágio só atualiza as contagens.
- **AC-6** — Dado um estágio escolhido sem eventos no dia selecionado (ou ao trocar de dia), quando a lista é exibida, então aparece "Nenhum evento neste estágio." e o filtro volta a "Todos" se o estágio escolhido deixar de existir no dia.
- **AC-7** — Dado o custo por evento nas linhas, quando o painel de custo mostra o total do dia, então o total continua vindo do daemon e o app não soma os custos das linhas.
**Out of scope:** Somar custo por estágio no app (o daemon já devolve `stages`); filtro por ator, por card ou busca; custo por evento de tipos que não sejam `RUN_COST_RECORDED`; mudar o protocolo do socket ou o daemon; histórico de outros dias além do seletor de dia que já existe; exportação.
**Product fit:** Serve "toda ação é um evento com ator" e "custo é estimativa, sempre rotulada" no feed; o app segue só cliente do daemon. Não contradiz o que está fora de escopo do produto.
**Touches UI:** yes

## UX
_UX/UI Designer_

- **Ator:** na linha, o ator vira um rótulo de texto `espécie · detalhe` (13px `muted`), sem cor própria; a espécie é dita por palavra, então nada depende só de cor. O protótipo `docs/design/interface-alvo.html` usa os mesmos nomes (fábrica, agente, dono, adaptador).
- **Custo por evento:** coluna à direita da linha, mono 12px tabular, `muted`, alinhada à direita; só em `RUN_COST_RECORDED`. PARTIAL leva `≥` e UNKNOWN `US$ ?`, com a tag `parcial`/`desconhecido` do painel (`warn`) e descrição de conteúdo para leitor de tela. Na janela estreita (< 560dp) o custo vai para a segunda linha, depois do ator.
- **Filtro por estágio:** faixa de chips abaixo do cabeçalho, acima da lista: "Todos" e um chip por estágio com a contagem (`Develop 12`). Chip é botão com `aria-pressed`-equivalente (`selected`), alvo >= 32dp e anel de foco `sand`; o selecionado muda de borda e peso (`edge-hi`, 600), não só de cor. Na janela estreita os chips quebram de linha, sem rolagem horizontal.
- **Estados:** estágio sem eventos mostra "Nenhum evento neste estágio." (14px `soft`); trocar de dia mantém o estágio escolhido se ele existir no novo dia, senão volta a "Todos".
- **Textos novos (pt-BR, em `Texts`):** "Todos", "dono", "agente", "fábrica", "adaptador", "Nenhum evento neste estágio.". Nomes de estágio vêm do evento (`refine`, `develop`, `validate`), exibidos com a inicial maiúscula.
- **Tokens:** só os de `DESIGN.md`/`Tokens.kt`; nenhuma cor nova.

## Architecture
_Tech Lead_

**Contratos**
- Sem mudança no daemon nem no socket: `events.recent` e `events.subscribe` já entregam `actor` (`owner`, `agent:<papel>`, `factory:<componente>`, `adapter:<provedor>`) e `payload` (`stage`, `status`, `cost_usd` em `RUN_COST_RECORDED`).
- `FeedRow` (`app/src/main/kotlin/sfv2/app/FeedModel.kt`) ganha `actorKind: String`, `actorDetail: String?`, `cost: String?`, `costTag: CostTag?` e `stage: String?`, todos com valor padrão (compatível com os usos atuais).
- `FeedState` ganha `stages: List<StageChip>` (`StageChip(stage: String?, count: Int)`, `null` = Todos) e `stage: String?` (selecionado). `EventFeedClient.selectStage(stage: String?)` filtra a mesma janela; `selectDay` preserva o estágio quando ele existe no dia.
- O texto do custo reaproveita a formatação de `CostSummaryView` (extrair `amountText`/`money` para `internal`); nada é somado no app (AC-7).
- Estágio vem só de `payload["stage"]`; evento sem estágio aparece apenas em "Todos".

**Camadas e módulos**
- `app`: `FeedModel.kt` (modelo e filtro), `FeedScreen.kt` (rótulo de ator, coluna de custo, chips), `Theme.kt` (`Texts`).
- `FeedTest.kt` cobre o modelo sem UI, com `Clock` fixo e `FakeFeedTransport`.

**Migração:** nenhuma.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- O corpo da issue #240 não veio com o card (só o título); o escopo foi derivado do título, do Feed existente (#68) e do protótipo `interface-alvo.html`.
- Camada única (`app`) e nenhuma mudança de protocolo: tudo o que a tela precisa já está em `actor` e `payload`; por isso peso `standard` (UI = yes impede `light`).
- "Histórico por estágio" vira filtro por estágio sobre a janela já carregada, não consulta nova: respeita o limite de 2000 eventos e o aviso `Histórico anterior indisponível` do AC-9 do #68.
- Custo por evento só em `RUN_COST_RECORDED`, o único tipo que carrega custo; o total por estágio continua no painel, vindo do daemon, para não recalcular no app.
- Estágio só de `payload["stage"]`: inferir estágio pela coluna do card exigiria estado que o feed não tem.
- Reaproveitar `FeedTest.kt` (suíte do módulo para feed e custo) em vez de arquivo novo.

## Open questions
- none

# Feed e custo — spec

**Issue:** #68 · **Branch:** `feat/68-feed-e-custo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O dono não vê no app o que a fábrica fez nem quanto gastou; hoje precisa de CLI ou banco para saber o que aconteceu e se o custo do dia está perto do limite.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, igualmente, o dentro de empresa que precisa da trilha do que foi feito).
**Acceptance criteria**
- **AC-1** — Dado o app conectado ao daemon, quando abro o feed do dia, então vejo os eventos do dia em ordem cronológica, cada um com tipo, ator, card e horário.
- **AC-2** — Dado o feed aberto, quando o daemon registra um novo evento, então ele aparece no feed sem recarregar a tela.
- **AC-3** — Dado que a conexão com o daemon caiu e voltou, quando o feed reconecta, então os eventos perdidos aparecem uma única vez, sem duplicatas nem lacunas.
- **AC-4** — Dado eventos de custo registrados no dia, quando vejo o painel de custo, então vejo o total do dia e o total por card, rotulados como estimativa.
- **AC-5** — Dado custo com status PARTIAL ou UNKNOWN, quando ele entra nos totais, então a tela marca o valor como parcial ou desconhecido, nunca como um número exato.
- **AC-6** — Dado que o projeto define limites de custo por card e por dia, quando vejo o painel, então cada total aparece ao lado do seu limite e destacado quando o atingiu ou ultrapassou.
- **AC-7** — Dado um projeto sem limite definido ou com limite indisponível, quando vejo o painel, então os totais aparecem sem limite e sem erro.
- **AC-9** — Dado o app abrindo o feed num daemon com muitos eventos acumulados, quando a tela carrega os eventos do dia, então o daemon devolve só os últimos 2000 eventos (com `truncated: true` quando há mais), a tela mostra do dia escolhido o que essa janela cobre e avisa `Histórico anterior indisponível` quando a janela não alcança o início do dia; abrir o feed nunca reproduz o histórico inteiro.
- **AC-10** — Dado o painel de custo, quando ele mostra o total do dia, o total por card, o limite e o destaque de limite atingido, então esses números vêm todos do daemon (a mesma conta do `CostBudget` que bloqueia os cards), nunca recalculados no app: o app só formata, e o painel nunca diz que está dentro do limite quando o daemon bloqueou o card.
- **AC-11** — Dado um dia em que a janela de eventos não cobre o início, quando o painel mostra o total do dia, então o total vem do daemon (que soma o dia inteiro por consulta indexada) e portanto não fica subestimado pela janela do feed.
- **AC-8** — Dado um dia sem eventos, quando abro o feed, então vejo um estado vazio explícito e custo zero.
**Out of scope:** Editar limites ou política de custo; alertas, notificações ou bloqueio ao estourar o limite; histórico de outros dias além de escolher o dia; filtros e busca no feed; exportação; custo em moeda que não seja USD.
**Product fit:** Serve "custo medido das transcrições de cada run" e "custo é estimativa, sempre rotulada" (AC-4, AC-5), e "toda ação é um evento com ator" (AC-1). Não contradiz o posicionamento nem o que está fora de escopo; o app é só cliente do daemon, sem escrever estado.
**Touches UI:** yes

## UX
_UX/UI Designer_

- **Fluxo:** com o daemon conectado (`ConnectionState.Connected`), a janela mostra a tela Feed no lugar do cartão de conexão; sem conexão, continua o `ConnectionScreen`. Uma única tela, sem navegação nova: o painel de custo fica acima da lista (janela larga) ou como primeiro bloco da coluna (janela estreita). Escolher o dia é um seletor com `‹` dia `›` e "Hoje"; `›` desabilitado e fraco em hoje (título "Este é o dia atual"). Fora de escopo: filtros e busca.
- **Linha do evento (AC-1):** ordem cronológica, mais novo no fim e a lista acompanha o fim enquanto o dono não rolou (se rolou, aparece o botão-texto "N novos eventos" que leva ao fim). Colunas: horário (mono 12px, `muted`, `HH:mm:ss`), marca de ator, tipo (14px 600, `text`), card (`#68`, `soft`), ator (13px `muted`). Marca por resultado, em forma e cor (nunca só cor): ponto `ok`, losango `bad` para falha, traço `faint` para linha sem desfecho; o acento `sand` não é usado (o app não mostra "espera o dono" nesta tela). Linha inteira é leitura, sem botão.
- **Tempo real (AC-2/AC-3):** evento novo entra na lista sem piscar nem mover a rolagem do dono; o cabeçalho tem região `liveRegion = Polite` só com o estado da conexão, a lista não anuncia cada linha. Ao reconectar, o cabeçalho mostra "Reconectando…" (ponto `warn`) mantendo a lista visível; ao voltar, os perdidos entram uma vez em seus horários, sem separador nem marcador especial, e o aviso some.
- **Painel de custo (AC-4):** painel `panel` com borda `edge`, raio 16dp, padding 16dp (12dp na janela estreita). Título "Custo do dia" (17px 600) com tag "estimativa" (`muted`, raio 4dp); total do dia em 24px 600 tabular, `text`, prefixo "US$" e sufixo "est." (Estimate Rule). Abaixo, a lista "Por card": `#id`, título curto truncado com reticências, valor à direita, alinhado por dígitos tabulares; ordenada do maior para o menor.
- **Parcial/desconhecido (AC-5):** PARTIAL mostra o valor com prefixo "≥" e a tag "parcial" (`warn`); UNKNOWN mostra "US$ ?" (nunca zero) e a tag "desconhecido" (`warn`), ambos com a explicação "Sem preço ou transcrição completa; o valor real pode ser maior." em 12px `muted` logo abaixo, uma vez por painel. Um total que inclui qualquer parcial/desconhecido herda "≥" e a tag "parcial". Estado dado por texto e forma, não só cor.
- **Limites (AC-6/AC-7):** com limite, o valor vira "US$ 3,20 de US$ 5,00" e uma barra de 4dp (raio 2dp, trilho `edge`, preenchimento `text`) mostra a proporção; ao atingir (>= 100%) a barra e o valor passam a `bad`, ganham o rótulo "limite atingido" (ou "limite ultrapassado" se >) e um quadrado cheio como marca. Sem limite ou limite indisponível: só o valor, sem barra, sem "de", sem erro e sem texto extra.
- **Estados:** carregando (primeira leitura) "Lendo o feed…" no lugar da lista e "US$ —" no total (nunca 0), sem esqueleto animado; vazio (AC-8) lista com "Nenhum evento neste dia." (15px `soft`) e custo "US$ 0,00 est." exato; erro de leitura do feed com a conexão viva: faixa `warn` no topo "Não consegui ler o feed. Tentando de novo." mantendo o último conteúdo; sem conexão: cai no `ConnectionScreen` existente.
- **Janela pequena:** mínimo 360x240 (já definido) e padrão 640x400. Abaixo de ~560dp o painel de custo ocupa a largura toda acima da lista e a linha do evento quebra em duas: horário + tipo na primeira, card + ator na segunda (13px `muted`); a janela rola numa só coluna (painel + lista), sem rolagem horizontal. Nada abaixo de 12px, texto corrido até 64ch.
- **Acessibilidade:** alvo do seletor de dia e "N novos eventos" com no mínimo 32dp e foco visível em `sand` (anel 2dp, a única exceção do acento por ser o foco); tag e marca têm descrição de conteúdo ("falhou", "parcial", "limite atingido"); contraste dos textos `muted`/`faint` conforme os tokens Charcoal; sem animação além da entrada instantânea da linha (nada a reduzir).
- **Componentes e tokens:** reaproveitar o painel/cartão, o ponto de estado e o `Tokens`/`Texts` do `ConnectionScreen` (extrair `Tokens` de `private` para `internal` e acrescentar `edge-hi`, `faint`, `bad`; sem cor fora do `DESIGN.md`). Fontes: `SansSerif` e `Monospace` já usadas (Plex não embutida no app ainda). Componentes novos só: linha de evento, barra de limite e tag; o resto é composição do que existe.
- **Textos novos (pt-BR, único idioma do app hoje; todos em `Texts`):** "Feed do dia", "Hoje", "Dia anterior", "Próximo dia", "Este é o dia atual", "Reconectando…", "N novos eventos", "Custo do dia", "Por card", "estimativa", "est.", "parcial", "desconhecido", "limite atingido", "limite ultrapassado", "de", "Lendo o feed…", "Nenhum evento neste dia.", "Não consegui ler o feed. Tentando de novo.", "Sem preço ou transcrição completa; o valor real pode ser maior.". Moeda no formato `US$ 1.234,56`, horário 24h.

## Architecture
_Tech Lead_

**Contratos**
- Existente, só leitura: `events.subscribe` (`{"cmd":"events.subscribe","after":<seq>}`); linhas `{"event":{seq,card,id,at,actor,type,payload}}`; `RUN_COST_RECORDED` traz `status` (KNOWN|PARTIAL|UNKNOWN) e `cost_usd`.
- Novo (daemon), leitura, `events.recent`: `{"cmd":"events.recent","limit":<=2000}` → `{"ok":true,"result":[<eventos no formato de renderEvent, em ordem de seq>],"truncated":<bool>}`: os últimos N eventos, sem varrer o histórico (consulta por `seq` decrescente com limite); depois o app assina com `after=<último seq>`. Não existe consulta por dia hoje e `events.subscribe` sem `after` só traz eventos novos, então o feed abre com essa janela (AC-9).
- Novo (daemon), leitura, `cost.summary`: `{"cmd":"cost.summary","from":"<instante ISO>","to":"<instante ISO>"}` → `{"ok":true,"result":{"totalUsd":"…","status":"KNOWN|PARTIAL|UNKNOWN|NONE","cards":[{"card","costUsd","status"}],"limits":{"perCardUsd":"<decimal>|null","perDayUsd":"<decimal>|null"},"dayReached":<bool>,"cardsReached":["<id>"]}}`. Calculado no daemon com `store.costEventsBetween` e a mesma conta de `CostBudget` (único lugar que decide "limite atingido" e bloqueia o card), limites de `DispatcherSettings.limits`. O app só formata (AC-10, AC-11). `Request.EventsRecent`, `Request.CostSummary` e as respostas em `Protocol.kt`, tratados em `SocketServer.kt`; o `CostSummary` do daemon fica em `daemon/.../CostSummaryQuery.kt`.
- Novo (app, interno): `EventFeedClient` (assina o stream, guarda o último `seq`, reconecta com `after=<último seq>` e descarta `seq` já visto), `FeedEvent`, `FeedState`, `CostSummaryView` (só formata o que o daemon devolveu: prefixo `≥`, tag `parcial`/`desconhecido`, `US$ ?`, nunca recalcula total nem limite), `CostSummarySource` (`fun interface`; falha → painel sem limites e sem destaque, nunca erro), `FeedScreen` e `CostPanel` (Compose), tokens compartilhados em `Tokens.kt` (extraídos de `ConnectionScreen`).

**Camadas e módulos**
- `app` (`app/src/main/kotlin/sfv2/app/`): arquivos acima; `Main.kt` passa a mostrar `FeedScreen` quando `Connected`.
- `daemon`: `Protocol.kt`, `SocketServer.kt` (comando `cost.limits`).
- O dia é o dia local do app (`Clock` + `ZoneId` por parâmetro); a agregação não lê relógio do sistema.

**Migração:** nenhuma (sem esquema novo; comando aditivo, sem quebra de contrato).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-8 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-9 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-10 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |
| AC-11 | app | `app/src/test/kotlin/sfv2/app/FeedTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- PO: limites vêm do `factory.yml` (`cost`); o app os obtém pelo novo comando `cost.limits` do daemon (nenhum comando atual os devolve).
- Peso `heavy`: duas camadas (`app` e `daemon`).
- `FeedTest.kt` é arquivo novo: o módulo `app` não tem suíte de feed; testa a agregação e o cliente com fakes e `Clock` fixo, sem UI.
- AC-6 é verificado na camada `daemon` (o comando entrega os limites); o destaque "atingido/ultrapassado" é função pura de `CostSummary`, coberta em `FeedTest.kt` junto de AC-4/AC-5.
- Ordem do feed: mais antigo primeiro, como pede AC-1 (DESIGN.md da V0 usa mais novo primeiro).

## Open questions
- **Limites:** A (recomendado) incluir o comando `cost.limits` no daemon neste card (2 camadas, `heavy`) ou B só o app, com limites sempre indisponíveis até outro card — com B o AC-6 não se cumpre em produção.

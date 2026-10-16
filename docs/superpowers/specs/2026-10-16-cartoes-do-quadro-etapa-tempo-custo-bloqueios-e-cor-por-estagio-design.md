# Cartões do quadro: etapa, tempo, custo, bloqueios e cor por estágio — spec

**Issue:** #237 · **Branch:** `feat/237-cartoes-do-quadro-etapa-tempo-custo-bloqueios-e-cor-por-estagio` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O cartão do quadro do app mostra só o título e o id. O dono não vê de relance em que etapa o card está, há quanto tempo está nela, quanto já custou nem se está bloqueado; `card.list` já devolve `blocked`, mas o app descarta o campo. O alvo decidido em `DESIGN.md` ("Cards on the board" e "Color by stage") pede esses dados no cartão e a cor do estágio no quadro.
**Audience:** o dono da fábrica, olhando o quadro para decidir o que fazer a seguir.
**Acceptance criteria**
- **AC-1** — Given cards em colunas diferentes, um deles com bloqueio aberto e outro com runs com custo registrado, when o cliente envia `card.list`, then cada item traz, além de `id`, `title`, `column` e `blocked`, o instante em que o card entrou na coluna atual e o custo total com seu status (conhecido, parcial, desconhecido ou nenhum), sem gravar evento e sem remover nenhum campo que a resposta já tinha.
- **AC-2** — Given uma resposta de `card.list` de um daemon antigo, sem os campos novos, when o app a lê, then o card aparece com bloqueio falso, sem tempo e sem custo, e nenhum card é descartado por isso.
- **AC-3** — Given um card com instante de entrada e um `Clock` fixo, when o app monta o texto do cartão, then mostra a etapa com o nome verbatim da coluna, o tempo na etapa ("há 6 min", "há 1 h 05 min", "há < 1 min") e o custo marcado `est.` em `BigDecimal`; custo parcial mostra `est.` com o aviso de parcial, e custo desconhecido ou ausente mostra "custo desconhecido" e nunca zero.
- **AC-4** — Given um card com bloqueio aberto, when o cartão é desenhado, then mostra "bloqueado" na cor `bad`, com o texto na descrição de acessibilidade, e o card continua na coluna em que está.
- **AC-5** — Given as dez colunas do quadro, when o app decide a cor de cada uma, then Refinement usa `refine`, Coding usa o novo `develop`, Validation usa `validate`, Ready for Prod e Done usam `ok`, Spec Review e Staged mantêm o acento e Inbox e as filas ficam neutras; a cor aparece só na borda superior de 3 px da coluna e no número do card, nunca como preenchimento nem em texto corrido.
- **AC-6** — Given cards cujo título contém texto de instrução ou marcação, when o cartão é desenhado, then o título é desenhado como texto simples e os rótulos de tempo, custo e bloqueio nascem só de números e ids, nunca do título.
**Out of scope:** barra de passos do pipeline e o dial do card em execução (exigem o passo atual do agente, trabalho novo no daemon); "bloqueado por #n" (o bloqueio aberto não guarda o card que bloqueia); chips de camada, gates, PR e CI; botões Decide e View spec; densidade em chips; painel lateral; minimapa e colunas redimensionáveis (outros cards).
**Product fit:** o quadro deve dizer o estado de cada card sem abrir a CLI; números de custo sempre rotulados como estimativa.
**Touches UI:** yes

## UX
_UX/UI Designer_

Segue `DESIGN.md` ("Target: the Board", "Cards on the board" e "Color by stage"); a única adição é o token `develop`, que o próprio `DESIGN.md` já define (`#7fcdbf` escuro, `#17806f` claro).
- Cartão: número (mono, na cor do estágio), à direita o estado; abaixo o título (14 px) e uma linha de meta com etapa, tempo e custo `est.`.
- Bloqueado: marca "bloqueado" em `bad` ao lado do número; sem fundo colorido.
- Acessibilidade: a descrição do cartão inclui título, id, etapa, tempo, custo e bloqueio; a cor nunca é o único sinal (o texto da etapa e "bloqueado" estão sempre presentes); contraste dos textos mantido pelos tokens existentes.
- Verificação visual com captura em desktop e em janela estreita.

## Architecture
_Tech Lead_

- `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `CardView` ganha `enteredAt` e `cost` com padrão nulo; `cardJson` os escreve só quando existem (aditivo; `blocked` e os demais campos ficam como estão).
- `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt`: `viewOf` preenche os campos a partir dos eventos do card (último `CARD_MOVED` para a coluna atual) e do total de custo que `card.detail` já usa; leitura pura, sem evento.
- `app/src/main/kotlin/sfv2/app/BoardSource.kt`: `parseCard` lê os campos novos e tolera a ausência deles.
- `app/src/main/kotlin/sfv2/app/BoardState.kt`: `BoardCard` ganha `blocked`, `enteredAt` e `cost` com padrão; modelo puro do cartão (rótulos de etapa, tempo e custo com `Clock` por parâmetro) e o mapa coluna para matiz de estágio.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: desenha a borda superior da coluna, o número na cor do estágio, a linha de meta e a marca de bloqueio.
- `app/src/main/kotlin/sfv2/app/Theme.kt`: token `develop` em cada tema existente e textos pt-BR do cartão em `Texts`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-6 | manual | `app/src/main/kotlin/sfv2/app/BoardScreen.kt` |

## Security
_Cybersec_

- Título, texto de issue e spec são dados não confiáveis: o título é só desenhado, como texto simples, e nenhum rótulo novo (tempo, custo, bloqueio) é derivado dele.
- `card.list` segue leitura: sem token do dono, sem evento, sem efeito fora da máquina; a resposta não ganha texto livre, só instante, status e valor de custo.
- Log do daemon com `key=value` e id do card; nunca título nem payload.
- O comando não recebe parâmetros novos que virem caminho, comando ou consulta.

## Rollback
Reverter o PR. Os campos de `card.list` são aditivos: um app antigo os ignora e um app novo diante de um daemon antigo mostra o cartão sem tempo e sem custo (AC-2). Nenhuma migração nem dado persistido.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Campos novos em `card.list` em vez de uma chamada por card: o quadro já lê a lista de uma vez, e N chamadas a `card.detail` multiplicariam a carga a cada evento.
- O app só desenha: tempo na etapa e custo vêm do daemon; o app formata com `Clock` por parâmetro, sem ler o banco.
- "Bloqueado" sem o id de quem bloqueia: o bloqueio aberto não registra o card causador, e inventá-lo violaria "nunca inventa estado"; o rótulo "bloqueado por #n" entra em card próprio quando o evento o carregar.
- Barra de passos e dial ficam fora: exigem o passo atual do agente, que o daemon ainda não expõe.
- Testes de AC-2 a AC-5 em `BoardStateTest.kt`: é a suíte do modelo do quadro no módulo `app`, e o modelo do cartão fica puro para ser testado ali; arquivo novo só repetiria a configuração.
- AC-6 é `manual`: renderização Compose não tem suíte automatizada no módulo; verificação por captura. A parte do daemon (resposta sem texto livre novo) já é coberta pelo teste de AC-1.
- Peso heavy: duas camadas (daemon, app) e `## Security` com conteúdo.

## Open questions
- none

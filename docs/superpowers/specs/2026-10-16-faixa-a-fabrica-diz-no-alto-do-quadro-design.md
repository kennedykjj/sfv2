# Faixa "A fábrica diz" no alto do quadro — spec

**Issue:** #235 · **Branch:** `feat/235-faixa-a-fabrica-diz-no-alto-do-quadro` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O quadro do app mostra colunas e cards, mas não diz o que a fábrica está fazendo agora. O estado do assistente (#73) e a frase (#74) existem só no daemon, sem caminho até a tela; o dono não vê de relance o estado em palavras, as sessões em uso nem o gasto do dia contra o teto. O alvo decidido em `DESIGN.md` ("The pulse strip") pede essa faixa entre o cabeçalho e o quadro.
**Audience:** o dono da fábrica, olhando o quadro para decidir o que fazer a seguir.
**Acceptance criteria**
- **AC-1** — Given um daemon com cards em andamento e esperando o dono, when o cliente envia `factory.pulse`, then a resposta traz o estado do assistente, a frase desse estado, a capacidade do pool e os ids dos cards com run ativo, sem gravar nenhum evento.
- **AC-2** — Given a requisição `factory.pulse`, when o daemon lê o JSON da linha, then ela é reconhecida sem campos obrigatórios, e uma forma inválida recebe erro que diz o que faltou.
- **AC-3** — Given uma pulsação do daemon e o resumo de custo de hoje, when o app monta a faixa, then ela mostra o estado em palavras com a frase, o pool como quadrados usados/total, e o teto do dia como medidor com o gasto marcado `est.`; sem teto configurado mostra "sem teto" e nenhum medidor.
- **AC-4** — Given que o daemon não responde ou a conexão caiu, when a faixa é montada, then ela mantém a última leitura esmaecida com o aviso de que está desatualizada, e nunca inventa estado, frase ou número.
- **AC-5** — Given a aba Quadro aberta e conectada, when a janela é desenhada, then a faixa aparece entre o cabeçalho e o quadro (e só na visão Quadro), em 3 células, em 2 por 2 abaixo de 1100 px e em 1 coluna abaixo de 880 px, com a frase em Plex Mono e o cursor parado sob movimento reduzido.
- **AC-6** — Given cards cujo título contém texto de instrução ou marcação, when a pulsação é enviada e desenhada, then a resposta e a faixa citam só rótulos `#n` ou 8 hex, nunca o título, e o texto é desenhado como texto simples.
**Out of scope:** previsão de fila vazia (a célula "Previsão" do protótipo; exige tempo por coluna e caminho crítico, trabalho novo no daemon); "Onde o tempo vai"; paleta de comandos; skins do assistente (#75, #76); cor por estágio no quadro.
**Product fit:** a fábrica deve ser legível sem abrir a CLI; a faixa dá ao dono o estado, a carga e o custo num lugar só, com números sempre rotulados como estimativa.
**Touches UI:** yes

## UX
_UX/UI Designer_

Segue `DESIGN.md` ("Target: the Board") e o protótipo `docs/design/interface-alvo.html`; nada de cor, fonte ou componente novo.
- Células: **A fábrica diz** (estado `▸ trabalhando` + frase, mono, cursor piscando), **Agora** (`n de N sessões` + quadrados do pool + pílulas dos cards ativos), **Teto do dia** (medidor + `est. US$ …` + percentual).
- Estados da faixa: carregando ("Lendo a fábrica…"), ao vivo, desatualizada (esmaecida + aviso em `warn`, `liveRegion` polite), sem teto ("sem teto").
- O acento areia não é usado aqui (a faixa não espera ação do dono); o medidor usa `ok`/`warn`/`bad` pelo estado do limite que o daemon informa.
- Acessibilidade: cada célula tem `contentDescription`; o medidor descreve "x% do teto do dia"; alvos de 24 px; cursor da frase é uma das quatro animações contínuas e para sob movimento reduzido.
- Verificação visual com captura em desktop (≥1100 px) e estreito (<880 px).

## Architecture
_Tech Lead_

- `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: nova requisição `Request.FactoryPulse` para `factory.pulse` (aditiva; nenhum comando existente muda).
- `daemon/src/main/kotlin/sfv2/daemon/FactoryPulse.kt` (novo): monta a resposta; estado por `AssistantStates.derive` (core), frase por `AssistantPhrases.say`, fatos só com rótulos `#n`/8 hex; pool por `RunPool`.
- `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: roteia o comando; leitura pura, sem evento.
- `app/src/main/kotlin/sfv2/app/PulseSource.kt` (novo): cliente do socket para `factory.pulse`, no padrão de `SocketCostSummarySource`.
- `app/src/main/kotlin/sfv2/app/PulseModel.kt` (novo): modelo puro (estado em palavras, quadrados, medidor, desatualizado), sem Compose; teto do dia vem de `cost.summary` já existente.
- `app/src/main/kotlin/sfv2/app/PulseStrip.kt` (novo): composable da faixa.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt` e `app/src/main/kotlin/sfv2/app/Main.kt`: encaixa a faixa acima das colunas e liga a leitura periódica (a cada 5 s e a cada evento de quadro).
- `app/src/main/kotlin/sfv2/app/Theme.kt`: textos pt-BR da faixa em `Texts`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProtocolTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-5 | manual | `app/src/main/kotlin/sfv2/app/PulseStrip.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_

- A frase e os rótulos nascem de ids e contagens do banco; título, texto de issue e spec nunca entram na resposta (AC-6). O app desenha tudo como texto simples, sem interpretar.
- `factory.pulse` é leitura: não exige token do dono, não grava evento, não abre efeito fora da máquina.
- Log do daemon com `key=value` e id do card; nunca a frase inteira nem payload.
- Entrada externa: o comando não recebe parâmetros que virem caminho, comando ou consulta.

## Rollback
Reverter o PR. O comando é aditivo no socket: um app antigo não o chama e um daemon antigo recusa `factory.pulse` com erro de comando desconhecido, caso em que a faixa cai no estado "desatualizada" (AC-4). Nenhuma migração nem dado persistido.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Comando novo `factory.pulse` em vez de o app derivar o estado: a regra do assistente fica no `core`/daemon e o app só desenha (cliente nunca lê o banco).
- Teto do dia reusa `cost.summary`: já existe, já devolve limites e `dayReached`; evita contrato duplicado.
- Previsão fica fora: o protótipo marca como trabalho novo do daemon; a faixa nasce com 3 células e a quarta entra em card próprio.
- Testes de AC-3/AC-4 em `BoardStateTest.kt`: é a suíte do modelo do quadro no módulo `app`, e arquivo novo só repetiria a configuração; o modelo da faixa fica puro para ser testado ali.
- AC-5 é `manual`: renderização Compose e layout responsivo não têm suíte automatizada no módulo; verificação por captura em desktop e estreito.
- Peso heavy: duas camadas (daemon, app) e `## Security` com conteúdo.

## Open questions
- none

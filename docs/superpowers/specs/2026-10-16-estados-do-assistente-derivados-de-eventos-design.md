# Estados do assistente derivados de eventos — spec

**Issue:** #73 · **Branch:** `feat/73-estados-do-assistente-derivados-de-eve` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o assistente (fase 7) precisa saber em que estado a fábrica está para escolher fala e skin. Hoje não há uma regra única, testada, que traduza o log de eventos num estado.
**Público:** o desenvolvedor sozinho com muitos projetos (olha de relance e quer saber se precisa agir); também o dono dentro de empresa.

**Critérios de aceitação** (função pura no `core`, sobre `List<Event>` de `Event.kt`; instante atual, janela de noite e limiares entram por parâmetro; sem IO nem relógio do sistema):
- AC-1 (ocioso): Dado um log sem card em andamento, aguardando o dono nem bloqueado, e fora da noite, quando o estado é derivado, então é `OCIOSO`.
- AC-2 (trabalhando): Dado ao menos um card numa coluna de trabalho da fábrica (Refinement, Coding, Validation ou Ready for Prod, que a fábrica entrega ou espera o merge e o dono já agiu) e nenhum bloqueio em aberto nele, quando o estado é derivado, então é `TRABALHANDO`.
- AC-3 (esperando o dono): Dado um card em Spec Review ou em Staged (as duas colunas em que a decisão é só do dono), quando o estado é derivado, então é `ESPERANDO_DONO`.
- AC-4 (bloqueado): Dado um log com `CARD_BLOCKED` sem movimento posterior do dono no mesmo card (`CARD_MOVED` com ator dono ou `OWNER_APPROVED`, a mesma regra do `CostBudget.isBlocked`; um movimento da fábrica não desbloqueia), quando o estado é derivado, então é `BLOQUEADO`.
- AC-5 (noite): Dado o instante atual dentro da janela noturna recebida por parâmetro (hora inicial e final, podendo cruzar a meia-noite, no fuso recebido), quando o estado é derivado, então é `NOITE`, e fora da janela nunca é `NOITE`.
- AC-6 (cansado): Dado ao menos N eventos de falha (`CARD_BLOCKED` ou `GATE_FINISHED` com resultado falho) nos últimos W minutos antes do instante atual, quando o estado é derivado, então é `CANSADO`; falhas mais antigas que W não contam.
- AC-7 (precedência): Dado um log que satisfaz vários estados, quando o estado é derivado, então vale a ordem `NOITE` > `CANSADO` > `BLOQUEADO` > `ESPERANDO_DONO` > `TRABALHANDO` > `OCIOSO`, com um teste por par adjacente.
- AC-8 (determinismo): Dados o mesmo log, instante e parâmetros, quando derivado duas vezes (inclusive com a lista de eventos embaralhada), então o resultado é idêntico; log vazio resulta em `OCIOSO` (ou `NOITE` na janela).

**Fora de escopo:** fala, skins, modo silencioso, UI, persistência do estado, leitura do log do banco, configuração pelo dono dos limiares (defaults fixos por ora).

**Aderência ao produto:** serve "toda ação é um evento": o estado é derivado do log, não guardado. Não contradiz posicionamento nem o "não é".

**Touches UI:** no.

**Decisões de produto (defaults, ajustáveis por parâmetro):** noite = 22:00 a 07:00 no fuso do dono; cansado = N=3 falhas em W=60 minutos. Noite vence tudo porque o assistente fica quieto fora do horário, apesar de a fábrica seguir trabalhando.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada única: `core` (puro, sem IO, sem relógio). Nenhum contrato externo muda; sem migração.
- Arquivo novo `core/src/main/kotlin/sfv2/core/AssistantState.kt`:
  - `enum class AssistantState { IDLE, WORKING, WAITING_OWNER, BLOCKED, NIGHT, TIRED }` (os nomes em português da spec são os rótulos de produto; o código segue o inglês).
  - `data class AssistantStateRules(val nightStart: LocalTime = 22:00, val nightEnd: LocalTime = 07:00, val zone: ZoneId, val tiredFailures: Int = 3, val tiredWindow: Duration = 60 min)`.
  - `object AssistantStates { fun derive(cardEvents: Map<String, List<Event>>, now: Instant, rules: AssistantStateRules): AssistantState }`.
- Entrada por card: `AssistantCard(column: Column, events: List<Event>)` (o `cardId` fica fora, `Event` não o carrega), com a coluna lida do card guardado e não deduzida dos eventos: cards nascidos direto numa coluna (importados da V0, reconstruídos do forge) têm só `CARD_CREATED` sem coluna, e a aprovação do dono em Spec Review grava `OWNER_APPROVED` e não `CARD_MOVED`, então deduzir pelo último `CARD_MOVED` erraria a coluna. `derive(cards: List<AssistantCard>, now, rules)`. A função ordena cada lista de eventos por `at` (desempate pela posição original) antes de ler.
- Regras por card: trabalhando = coluna `REFINEMENT`, `CODING`, `VALIDATION` ou `READY_FOR_PROD` e sem bloqueio em aberto. Esperando = coluna `SPEC_REVIEW` ou `STAGED`. Bloqueio em aberto = `CARD_BLOCKED` sem `CARD_MOVED` de ator dono nem `OWNER_APPROVED` depois dele no mesmo card.
- Falha (para cansado) = `CARD_BLOCKED`, ou `GATE_FINISHED` com `payload["verdict"] == "FAIL"` (chave gravada hoje por `DevelopPipeline.recordScope`), com `at` em `(now - tiredWindow, now]`, somando todos os cards.
- Precedência: `NIGHT > TIRED > BLOCKED > WAITING_OWNER > WORKING > IDLE`. Janela noturna usa `now` no `zone`, com `nightStart > nightEnd` cruzando a meia-noite; início inclusivo, fim exclusivo.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-5 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-6 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-7 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-8 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo `AssistantStateTest.kt`: nenhuma suíte do `core` cobre o assistente.
- Entrada `Map<cardId, List<Event>>`: o evento não carrega o id do card e o estado depende de movimentos por card.
- Janela noturna e limiares são parâmetros com defaults 22:00–07:00 e 3 falhas em 60 min: o PO fixou os valores, o dono ajusta depois.
- Falha de gate = `verdict == "FAIL"`: é a chave que o daemon já grava; nenhum evento novo.
- A issue cita bloqueio por #17 (fechada) e o roadmap por #63; nada disso trava a spec, pois a função só usa tipos já existentes no `core`.

## Open questions
- none

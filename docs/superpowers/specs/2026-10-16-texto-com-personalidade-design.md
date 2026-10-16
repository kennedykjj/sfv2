# Texto com personalidade — spec

**Issue:** #74 · **Branch:** `feat/74-texto-com-personalidade` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** O assistente já sabe em que estado está (#73), mas ainda não tem o que dizer. Falta a frase por estado, montada só com dados do banco, para que nenhuma fala contradiga a fábrica.
**Audience:** O dono, que lê o assistente nas skins (terminal, pop-up) e em casa.
**Acceptance criteria**
- **AC-1** — Dado um estado do assistente e os fatos do banco (ids e contagens de cards), quando a frase é pedida, então ela vem de um modelo de texto do estado preenchido com esses fatos, sem modelo de linguagem e com o mesmo resultado a cada chamada.
- **AC-2** — Dada a voz por modelo ligada, quando a frase é pedida, então o modelo recebe só a frase já montada para reescrever, e a saída é usada apenas se mantiver todos os ids e números da frase original; caso contrário, ou se a voz falhar, vale a frase montada.
- **AC-3** — Dada a voz por modelo desligada (padrão), quando a frase é pedida, então a voz nunca é chamada e vale a frase montada.
**Out of scope:** Skins (#75, #76), modo silencioso, escolha do modelo de voz em Configurações, texto livre do dono ou de issue na frase.
**Product fit:** Cumpre a decisão 12: o que o assistente diz vem do banco; o modelo só dá voz.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada `daemon`, pacote `sfv2.daemon`; sem mudança de contrato público existente, sem migração.

- `daemon/src/main/kotlin/sfv2/daemon/AssistantPhrases.kt` (novo):
  - `data class AssistantFacts(waitingOwner: List<Int>, blocked: List<Int>, working: List<Int>, recentFailures: Int)` — só ids e contagens, nunca título nem texto de issue.
  - `object AssistantPhrases { fun assemble(state: AssistantState, facts: AssistantFacts): String }` — um modelo de texto por estado (`IDLE`, `WORKING`, `WAITING_OWNER`, `BLOCKED`, `NIGHT`, `TIRED`), função pura e determinística, em português.
  - `fun interface AssistantVoice { fun rewrite(phrase: String): String }` — port mínima do modelo de voz.
  - `class AssistantSpeaker(voice: AssistantVoice?, voiceEnabled: VoiceMode)` com `fun say(state, facts): String`; `enum class VoiceMode { OFF, ON }` (booleano que escolhe comportamento vira enum).
  - Validação da reescrita: todo token numérico (ids `#n` e contagens) da frase montada precisa aparecer na saída; senão cai na frase montada. Falha da voz (`RuntimeException`) também cai na frase montada, logada só com o estado.
- Fatos vêm do banco pelo chamador (fiação do daemon fica para a skin que consome, #75/#76); esta issue entrega a montagem e a reescrita, testáveis sem IO.
- O estado vem de `AssistantStates.derive` (core, #73), sem alteração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AssistantPhrasesTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AssistantPhrasesTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/AssistantPhrasesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Arquivo de teste novo `AssistantPhrasesTest.kt`: nenhuma suíte do daemon cobre o assistente.
- Frases só com ids e contagens: título de issue é texto não confiável e não entra na frase nem no prompt da voz.
- Reescrita só vale se preservar ids e números: garante "nenhuma frase contradiz o banco" mesmo com o modelo livre.
- Voz desligada por padrão e com fallback para a frase montada: o assistente nunca fica mudo nem depende do modelo.
- Fiação com o banco e o modelo real fica para as skins (#75, #76): a issue cobre só montagem e voz, como no escopo do card.

## Open questions
- none

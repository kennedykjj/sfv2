# Skin pop-up animado — spec

**Issue:** #76 · **Branch:** `feat/76-skin-pop-up-animado` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problema:** o assistente da fábrica precisa de uma segunda skin, o pop-up animado, que avisa a mudança de estado com a frase de personalidade e some sozinho, sem nunca incomodar quem ligou o modo silencioso. Neste card a skin entra como modelo puro e testável na camada `app` (estado novo -> comando de pop-up; fases da animação em função do tempo; modo NORMAL/SILENT), não como janela Compose (o shell é o #63).

**Audiência:** o desenvolvedor sozinho com muitos projetos, que deixa a fábrica trabalhando e quer ser avisado de relance quando algo muda.

**Critérios de aceitação**
- AC-1: Dado o modo NORMAL, quando o estado do assistente muda para um estado novo, então a skin emite um comando de pop-up com a frase correspondente ao estado e começa pela animação de entrada.
- AC-2: Dado o modo SILENT, quando o estado muda, então nenhum pop-up e nenhuma animação são emitidos.
- AC-3: Dado um pop-up já exibido para o estado X, quando o mesmo estado X é notificado de novo, então nenhum novo pop-up é emitido.
- AC-4: Dado um pop-up emitido, quando o tempo decorrido avança, então a fase vai de entrada, a permanência e a saída, e após a saída o pop-up deixa de existir, determinado só pelo tempo (Clock/Instant por parâmetro).

**Fora de escopo:** janela Compose e shell (#63); a skin do tray e outras skins; escolha do modo silencioso pelo usuário e persistência; som; texto das frases (já em AssistantPhrases); fila de vários pop-ups.

**Product fit:** serve ao compromisso de marca "assistente com skins, sempre com modo silencioso". Não contradiz o posicionamento nem o que está fora de escopo.

**Touches UI:** yes

## UX
_UX/UI Designer_

Este card entrega só o modelo; o renderizador (#63) lê dele o que segue, conforme a convenção de movimento do DESIGN.md (troca de card: sai em 260ms, entra em 400ms).
- **Conteúdo:** o comando de pop-up carrega só a frase do estado (`AssistantPhrases`, já em pt-BR e inglês). Nada de texto de card, título de issue ou id; um pop-up é uma linha curta.
- **Fases por tempo:** `ENTRY` 400ms (desliza da direita e aparece), `STAY` 4000ms (parado, legível de relance), `EXIT` 260ms (some com fade para a esquerda), depois `GONE`. Total 4660ms. Fase calculada de `Instant` de emissão e `Instant` atual por parâmetro; durações são constantes nomeadas no modelo, não literais espalhados.
- **Movimento reduzido:** o comando traz a variante reduzida como dado (`motion = REDUCED`): sem deslize e sem fade, a fase `ENTRY` tem duração 0 e a `EXIT` também; o pop-up aparece inteiro, fica `STAY` 4000ms e some de uma vez. Aparecer e sumir ficam só por tempo. O modelo recebe `reducedMotion` como entrada (enum `FULL | REDUCED`, não booleano); ler a preferência do sistema é do shell.
- **SILENT:** não emite comando, nem fase, nem animação; para o renderizador, nada a mostrar. Passar de NORMAL para SILENT com pop-up na tela encerra o pop-up na hora (`GONE`), sem saída animada.
- **Mesmo estado:** repetir o estado já exibido não reinicia o tempo nem a animação. Estado novo com pop-up em curso substitui o atual e reinicia na `ENTRY` (sem fila, conforme o escopo).
- **Estados de tela:** vazio = `GONE`; carregando e erro não existem neste modelo (a frase vem de estado já derivado).
- **Tela pequena, tokens e componentes:** responsabilidade do #63. O modelo só não pode impor tamanho; o renderizador usa os tokens do DESIGN.md, sem cor, fonte ou componente novo. O estado continua legível pela frase, sem depender de cor.

## Architecture
_Tech Lead_

- **Camada:** só `app` (depende de `core`; não depende de `daemon`, então a frase chega como `String` de quem chama).
- **Contratos (novo, `app/src/main/kotlin/sfv2/app/PopupSkin.kt`):**
  - `enum SkinMode { NORMAL, SILENT }`, `enum MotionPreference { FULL, REDUCED }`, `enum PopupPhase { ENTRY, STAY, EXIT, GONE }`.
  - `data class PopupCommand(val state: AssistantState, val phrase: String, val emittedAt: Instant, val motion: MotionPreference)`.
  - `PopupSkin` (imutável): `onState(state: AssistantState, phrase: String, mode: SkinMode, motion: MotionPreference, now: Instant): PopupSkin.Update` com `Update(skin: PopupSkin, command: PopupCommand?)`; `phase(now: Instant): PopupPhase`; `current: PopupCommand?`.
  - Constantes nomeadas: `ENTRY = 400ms`, `STAY = 4000ms`, `EXIT = 260ms`; em `REDUCED`, `ENTRY` e `EXIT` valem 0.
- **Regras:** SILENT -> sem comando e descarta o pop-up atual (`GONE`); mesmo estado do pop-up em curso -> sem comando, sem reiniciar; estado novo -> novo comando e fase `ENTRY`; a fase é função de `now - emittedAt`.
- **Arquivos:** `app/src/main/kotlin/sfv2/app/PopupSkin.kt`, `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` (2).
- **Migração:** nenhuma (sem banco, sem contrato externo). Sem janela Compose: o `app` é um stub até o #63.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- UX: permanência de 4000ms escolhida pela fábrica (o DESIGN.md só define 260/400ms); fica como constante fácil de ajustar.
- O modelo fica no `app` e recebe a frase como `String`: `app` só depende de `core`, e o texto vem do `daemon` pelo shell (#63).
- Arquivo de teste novo (`PopupSkinTest.kt`): nenhuma suíte do `app` cobre skins (só `ModuleTest.kt`, do placeholder).
- Modo silencioso é um parâmetro (`SkinMode`); onde o dono o liga e a persistência ficam fora do card.
- A interface comum de skins ainda não existe (#75 em paralelo): `PopupSkin` é autônomo e quem entrar depois adapta.
- Peso `standard`: uma camada, 4 critérios, mas `Touches UI: yes`.

## Open questions
- **Janela do pop-up:** A (recommended) entregar só o modelo testável e deixar a janela Compose para o #63 — nada fica visível até o shell existir; ou B incluir já a dependência Compose e a janela no `app` — amplia o card e mexe no toolchain.

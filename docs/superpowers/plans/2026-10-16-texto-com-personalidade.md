# Texto com personalidade — plano (#74)

**Spec:** `docs/superpowers/specs/2026-10-16-texto-com-personalidade-design.md`
**Peso:** light · **Camadas:** `daemon` (única)

## Tarefas

### T1 — Montagem e reescrita das frases (`daemon`)

Dependências: nenhuma. Responsável: Tech Lead (solo, peso light, uma camada).

Arquivos:
- `daemon/src/main/kotlin/sfv2/daemon/AssistantPhrases.kt` (novo)
  - `AssistantFacts`: rótulos validados por `^#[0-9]{1,10}$|^[0-9a-f]{8}$`; só rótulos e contagens.
  - `AssistantPhrases.assemble(state, facts)`: uma frase por estado (`IDLE`, `WORKING`, `WAITING_OWNER`, `BLOCKED`, `NIGHT`, `TIRED`), pura e determinística, em português.
  - `AssistantVoice` (fun interface, `rewrite(phrase)`), `VoiceMode` (`OFF`, `ON`).
  - `AssistantSpeaker.say(state, facts)`: sem voz ou com `VoiceMode.OFF` nunca chama a voz; com voz, roda `rewrite` com prazo de 3 s e aceita a saída só se: conjunto de tokens (sequências de dígitos e rótulos de 8 hexadecimais) idêntico ao da frase montada, até 280 caracteres, sem quebra de linha, sem `://`, sem crase, colchete ou `<`. Qualquer desvio, timeout ou `RuntimeException` cai na frase montada; o log leva só o estado.

Gates após a tarefa: `scope daemon <start>`, `acceptance-intact <qa>`, `migration <base> <spec>`, `build <base>`.

## Ordem

1. QA escreve `daemon/src/test/kotlin/sfv2/daemon/AssistantPhrasesTest.kt` (AC-1 a AC-5) e faz o commit de acceptance tests.
2. T1 implementa até os testes ficarem verdes.
3. Gates finais de `green` por seletor, `forbidden` e `migration` contra `origin/main`.

## Riscos

- Teste de timeout sem relógio real: o prazo de 3 s tem que ser controlado sem `Thread.sleep`; a implementação recebe o prazo como parâmetro para o teste poder usar um rewrite que não retorna.
- Validação de tokens: um número dentro de um rótulo de 8 hexadecimais (ex.: `12345678`) conta como um só token; a comparação é por conjunto exato.

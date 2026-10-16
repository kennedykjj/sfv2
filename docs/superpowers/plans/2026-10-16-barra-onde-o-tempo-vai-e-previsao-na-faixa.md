# Barra "Onde o tempo vai" — plano

Spec: `docs/superpowers/specs/2026-10-16-barra-onde-o-tempo-vai-e-previsao-na-faixa-design.md`

1. Testes primeiro em `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`: `TimeSplit.of` (ordem e segmentos, percentual com meio para cima, `null` sem estatística ou total zero, só nomes de coluna e números).
2. `BoardState.kt`: `TimeSegment`, `TimeSplit.of` em aritmética inteira, usando `isWaitingForOwner`.
3. `Theme.kt`: textos pt-BR da barra, da frase e da descrição de acessibilidade.
4. `FlowBar.kt`: composable com barra, hachura de espera, frase e legenda (oculta abaixo de 880 px).
5. `Main.kt`: encaixar entre `PulseStrip` e `BoardScreen`, esmaecida quando `!view.live`.
6. `./gradlew :app:test` e `./gradlew build`; capturas em desktop e estreito.

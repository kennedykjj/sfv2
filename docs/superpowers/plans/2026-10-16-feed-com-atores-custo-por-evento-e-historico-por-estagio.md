# Feed com atores, custo por evento e histórico por estágio — plano

Spec: `docs/superpowers/specs/2026-10-16-feed-com-atores-custo-por-evento-e-historico-por-estagio-design.md`

1. **Testes primeiro (QA)** em `app/src/test/kotlin/sfv2/app/FeedTest.kt`: um teste por AC-1 a AC-7, usando `FeedFixtures.anEvent` (estender com `payload`), `FakeFeedTransport` e `Clock` fixo. Devem falhar por falta de comportamento.
2. **Modelo** em `app/src/main/kotlin/sfv2/app/FeedModel.kt`:
   - `FeedRow` com `actorKind`, `actorDetail`, `cost`, `costTag`, `stage`.
   - Ator: separar `espécie:detalhe`, traduzir a espécie, manter o texto original se desconhecida.
   - Custo: só `RUN_COST_RECORDED`, por `status` e `cost_usd`; ilegível vira `US$ ?`; reaproveitar `amountText`/`money` (tornar `internal`).
   - `FeedState.stages` e `stage`; `EventFeedClient.selectStage`; `selectDay` preserva o estágio se existir no dia, senão volta a "Todos".
3. **Tela** em `FeedScreen.kt` e `Theme.kt`: rótulo de ator, coluna de custo com tag, chips de estágio com contagem e estado vazio do estágio; textos novos em `Texts`.
4. **Verificar:** `./gradlew :app:test` e `./gradlew build`; captura da tela em desktop e janela estreita (< 560dp).

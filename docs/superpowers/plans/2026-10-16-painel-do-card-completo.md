# Painel do card completo — plano

Spec: `docs/superpowers/specs/2026-10-16-painel-do-card-completo-design.md`

1. `CardDetailModelTest.kt`: testes vermelhos para `cardPullRequest`, `openBlock`, `costByStage` e `criterionResult` (AC-1 a AC-5), com fixtures de eventos e runs.
2. `CardDetailModel.kt`: implementar as quatro funções puras até os testes passarem.
3. `Theme.kt`: textos pt-BR novos em `Texts`.
4. `CardDetailScreen.kt`: Summary (bloqueio, PR, gates), Spec (critérios + texto) e Runs (por estágio + total).
5. `./gradlew :app:test`, depois `./gradlew build`; captura em janela larga e estreita (AC-6 manual).

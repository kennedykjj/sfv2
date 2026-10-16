# Aprovar e pedir mudança pelo app — plano

Spec: `docs/superpowers/specs/2026-10-16-aprovar-e-pedir-mudanca-pelo-app-design.md`

1. **Testes (QA, vermelho):** em `OwnerMovesTest.kt`, testes do `SpecReviewController` com `FakeOwnerCommands` (AC-1 a AC-5); em `CardDetailModelTest.kt`, atualização do detalhe após o evento do card (AC-6).
2. **Controller:** `SpecReviewController` e `SpecReviewState` em `OwnerMoves.kt`; visível só em `SPEC_REVIEW`; nota obrigatória via `needsNote`; flag `sending` contra envio duplo; sem estado otimista.
3. **Textos:** mensagens novas em `Texts` (`Theme.kt`): ações, "Aprovação enviada", "Enviando…", pedido de nota; recusa e não enviado reaproveitam `Texts.refused` e `Texts.notSent`.
4. **Tela:** faixa de ações em `CardDetailScreen.kt` (botões, campo de nota, Enter/Esc, região viva, mensagem de erro com rótulo).
5. **Fiação:** `OpenCard` em `Main.kt` cria o controller com `SocketOwnerCommands` e `Dispatchers.IO`.
6. **Verificação:** `./gradlew :app:test` e `./gradlew build`; captura da visão do card em janela larga e compacta (AC-7).

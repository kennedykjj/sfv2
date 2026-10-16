# Tela de fechamento — plano (#71)

Spec: `docs/superpowers/specs/2026-10-16-tela-de-fechamento-design.md`

1. **Contrato do socket** (`OwnerCommands.kt`): `ClosureCommands`, `closureDraftRequest`, `closureConfirmRequest`, `SocketClosureCommands` (token lido a cada envio), resultados em sealed types. Testes em `OwnerMovesTest.kt` (AC-3, AC-5).
2. **Controlador** (`ClosureModel.kt`): `ClosureController` com `ClosureState`, semente pelos eventos do card, `confirm()` ignorado em `Confirming`, retry com o mesmo `draftHash`, novo rascunho quando o daemon diz que mudou. Testes em `CardDetailModelTest.kt` (AC-1, AC-2, AC-4, AC-6, AC-7) com fake de `ClosureCommands`, sem sleep.
3. **Tela** (`ClosureScreen.kt`, `CardDetailScreen.kt`, `Theme.kt`): composable fino sobre o estado, textos em `Texts`, entrada só para card em Done.
4. **Ligação** (`Main.kt`): injetar `SocketClosureCommands` com o socket e o `owner.token` já usados por `OwnerMoveController`.
5. **Verificação**: `./gradlew :app:test` e `./gradlew build`; capturas em desktop e mobile da tela nos estados rascunho, recusado, fechado e erro.

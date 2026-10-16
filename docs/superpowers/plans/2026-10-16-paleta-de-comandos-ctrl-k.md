# Paleta de comandos Ctrl K — plano

Spec: `docs/superpowers/specs/2026-10-16-paleta-de-comandos-ctrl-k-design.md`

1. **Red (QA):** em `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`, um teste por AC (AC-1 a AC-5) sobre `paletteEntries` e `PaletteState`, com cards de `BoardFixtures`.
2. **Modelo:** criar `app/src/main/kotlin/sfv2/app/PaletteModel.kt` (entradas, filtro, limite de 9, estado com `toggle`/`close`/`move`).
3. **Textos:** adicionar os textos da paleta em `Texts` (`Theme.kt`).
4. **Tela:** criar `app/src/main/kotlin/sfv2/app/PaletteScreen.kt` (campo, linhas, linha ativa, "sem resultado", scrim).
5. **Ligação:** em `Main.kt`, `PaletteState`, captura de Ctrl K/Cmd K antes das teclas de uma letra, Enter abrindo card ou trocando de tela, foco devolvido ao fechar.
6. **Verificar:** `./gradlew :app:test` e `./gradlew build`; captura da janela com a paleta aberta (vazia, com filtro, sem resultado).

# Quadro de colunas com rolagem lateral e mapa — plano

Spec: `docs/superpowers/specs/2026-10-16-quadro-de-colunas-com-rolagem-lateral-e-mapa-design.md`

1. **Red (QA):** em `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`, um teste por AC-1 a AC-5 sobre `boardMap`, `scrollTarget` e `edgeFades` (proporção, contorno da janela, clamp do destino, quadro vazio e janela zero, bordas).
2. **Green (Engineer):** criar `app/src/main/kotlin/sfv2/app/BoardMap.kt` com as funções puras e os data classes.
3. Adicionar `COLUMN_MAP` e `goToColumn` em `Texts` (`app/src/main/kotlin/sfv2/app/Theme.kt`).
4. Em `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: mapa acima do quadro, bordas esmaecidas, cabeçalho de coluna fora do `verticalScroll` e coluna com `fillMaxHeight`; clique do mapa chama `scrollTo(scrollTarget(...))`.
5. `./gradlew :app:test` e `./gradlew build`; capturas do quadro em janela normal e mínima (360x240) para o AC-6.

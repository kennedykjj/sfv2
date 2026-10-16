# Colunas ajustáveis, ajustar à janela e densidade — plano

Spec: `docs/superpowers/specs/2026-10-16-colunas-ajustaveis-design.md`

1. **Red (QA):** em `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`, testes de `ColumnLayoutState` e `columnWidths` para AC-1 a AC-8 (limites, reset por coluna, equalizar, sobra proporcional, mínimo na janela estreita, colunas vazias recolhidas e restauradas, densidade preservando larguras, passo do teclado).
2. **Modelo:** criar `app/src/main/kotlin/sfv2/app/ColumnLayout.kt` com `BoardDensity`, `ColumnLayoutState` e `columnWidths` (puro, sem IO).
3. **Tela:** em `BoardScreen.kt`, usar `columnWidths` no lugar de `LANE_WIDTH`; puxador (arrastar, duplo clique, setas), barra com Ajustar à janela, Equalizar e densidade, faixa recolhida e fichas.
4. **Verificar:** `./gradlew :app:test` e `./gradlew build`; capturas do quadro em janela larga, estreita e com Fichas.

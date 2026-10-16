# Painel lateral do card — plano

Spec: `docs/superpowers/specs/2026-10-16-painel-lateral-do-card-design.md`

1. **QA, testes primeiro (vermelho)** em `app/src/test/kotlin/sfv2/app/BoardStateTest.kt`: abrir, trocar e fechar o painel (AC-1, AC-5); abas e caminho pelas dez colunas (AC-2); limites 360 a 680 e memória da largura (AC-3); limiar de 880 px (AC-4); rodapé por coluna (AC-6). Em `CardDetailModelTest.kt`, confirmar `NotFound` e `Disconnected` (AC-7). Fixtures em `object` existente, com `aCard(...)`.
2. **Modelo puro** em `BoardState.kt`: `SidePanelState`, `PanelTab`, `panelPlacement`, `cardPath`, `panelFooter`. Sem Compose, sem relógio.
3. **Textos** em `Theme.kt` (`Texts`): abas, caminho, estados.
4. **Painel** em `CardDetailScreen.kt`: moldura com topo, abas, corpo por aba (seções atuais) e rodapé fixo; borda de redimensionar focável.
5. **Quadro** em `BoardScreen.kt`: cartão clicável e por teclado, destaque do aberto, `onOpenCard`.
6. **Ligação** em `Main.kt`: painel ao lado ou sobreposto, paleta e notificação no mesmo caminho, Esc.
7. **Verificar:** `./gradlew :app:test` e `./gradlew build`; capturas em janela larga e estreita (AC-8 e a parte visual de AC-7).

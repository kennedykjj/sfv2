# Gates por card: resultado, duração e critérios — plano

Spec: `docs/superpowers/specs/2026-10-16-gates-por-card-resultado-duracao-e-criterios-design.md`

1. **Duração no evento (AC-1).** Teste em `GateExecutorTest` com `Clock` que avança; depois `GateExecutor` mede antes/depois de `runner.run` e grava `duration_ms`, também no timeout.
2. **Plano da spec (AC-3).** Extrair `SpecGates.plan(specText)` do parser de tabela existente; `check` passa a usá-lo.
3. **`card.detail` (AC-2, AC-3, AC-4).** Testes em `SocketServerTest` (gates sem duração, critérios com `none`/`manual`/veredito, card vazio); depois `CardDetail.build` monta `gates` e `criteria`.
4. **Modelo do app (AC-5).** Teste em `CardDetailModelTest` (com e sem os campos); depois `GateRow`/`CriterionRow` em `CardDetailModel`.
5. **Tela (AC-6).** Seção Gates em `CardDetailScreen` com formato de duração; verificar com captura em desktop e mobile.
6. **Fechar.** `./gradlew :daemon:test :app:test` e `./gradlew build`.

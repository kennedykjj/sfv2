# Porta única de texto externo — plano (#57)

Spec: `docs/superpowers/specs/2026-10-16-porta-unica-de-texto-externo-design.md`

## Tarefas

1. **daemon** (owner: engineer / Tech Lead solo, sem dependências)
   - `ReferenceView.url` passa de `String` para `UntrustedText("reference_url", url)` em `daemon/src/main/kotlin/sfv2/daemon/McpReadTools.kt`; ajustar o ponto de construção em `McpReadTools`.
   - Ajustar usos existentes de `ReferenceView.url` em testes do daemon só se quebrarem (sem tocar os testes de aceitação do QA).
   - Qualquer outro campo de texto sem envoltório que o teste de regressão (AC-4) achar entra pelo mesmo caminho.

## Testes (QA, antes da implementação)

- AC-1, AC-2, AC-4: `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt`.
- AC-3: `e2e/src/test/kotlin/sfv2/e2e/PhaseFourExitTest.kt` (arquivo novo).

# Entrega no PR: remover aprovação que fabrica ator dono sem prova — plano

**Issue:** #178 · **Spec:** `docs/superpowers/specs/2026-10-16-entrega-no-pr-remover-aprovacao-sem-prova-design.md` · **Peso:** light

## Tarefas

### Tarefa 1 — camada `daemon`, dono: `engineer`
Arquivos:
- `daemon/src/main/kotlin/sfv2/daemon/PullRequestHandoff.kt`: remover `approve(cardId)`; ajustar o KDoc da classe (a aprovação é o `card.move` do dono, gravado pelo board) e o de `HandoffOutcome.Waiting` (tirar "or the approval was just recorded"); remover o log `handoff_approved`. `move(...)` privado e `reconcile` ficam como estão.
- `daemon/src/test/kotlin/sfv2/daemon/PullRequestHandoffTest.kt`: o teste do AC-1 do #47 passa a usar `ownerApproves` e a verificar Ready for Prod, `forge.merges` vazio e `reconcile` = Waiting; novo teste por reflexão Java (`declaredMethods` públicos sem `approve`, com `reconcile`).

Dependências: nenhuma. Sem migração, sem outra camada, sem endpoint novo.

## Ordem

1. QA escreve os testes de aceitação (falhando) e faz o commit `acceptance tests for #178`.
2. Implementação da tarefa 1 e gates.

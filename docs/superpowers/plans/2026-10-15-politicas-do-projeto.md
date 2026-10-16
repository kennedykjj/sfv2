# Políticas do projeto — plano

**Issue:** #19 · **Spec:** `docs/superpowers/specs/2026-10-15-politicas-do-projeto-design.md`

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | core | engineer (solo, Tech Lead) | testes de aceitação (QA) | `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt` |

## Tarefa 1 — core

Criar `DeliveryMode`, `ExternalEffect`, `Signer`, `Conventions`, `ProjectPolicies` (`DEFAULT`, `from`) e `InvalidPolicyException`, conforme a seção Architecture da spec. Funções puras, sem IO nem YAML; a entrada é a árvore já parseada. Fazer passar `ProjectPoliciesTest` (escrito por QA antes da implementação).

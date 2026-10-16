# Entrega autônoma — plano (#46)

Spec: `docs/superpowers/specs/2026-10-16-entrega-autonoma-design.md`. Peso heavy, duas camadas; tarefas em sequência (compartilham o worktree).

## Tarefa 1 — core (agente: engineer)
- `core/.../MoveRules.kt`: linha `(READY_FOR_PROD to STAGED) to FACTORY_OR_OWNER`.
- `core/.../Event.kt`: `EventType.PR_MERGED` (payload `pr`, `merge_commit`, `head`).
- `core/src/test/kotlin/sfv2/core/MoveRulesTest.kt`: acompanhar a tabela.
- Depende de: nada.

## Tarefa 2 — daemon (agente: engineer)
- `daemon/.../AutonomousDelivery.kt` (novo): `deliver(cardId): DeliveryOutcome` conforme a seção Architecture (passos 1–5, idempotência por `PR_MERGED`, head aprovado do Staged do validate).
- `daemon/.../ProjectDelivery.kt`: extrair `forgeOf(project)` de `create`.
- `daemon/.../Dispatcher.kt`: `deliveries: DeliveryTrigger` opcional, chamado em `runCycle` para cada card em Ready for Prod, depois das checagens de pausa/horário/limite; `AutonomousDeliveryTrigger` de produção com executor de uma thread e conjunto de cards em andamento.
- `daemon/.../RefineRuntime.kt`: montar `AutonomousDelivery` e passar o trigger ao `Dispatcher`.
- Testes de acompanhamento: `AutonomousDeliveryTest.kt`, `DispatcherTest.kt` (os de aceitação vêm do QA).
- Depende de: tarefa 1.

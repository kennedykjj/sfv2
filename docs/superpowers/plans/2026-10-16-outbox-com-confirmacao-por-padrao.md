# Outbox com confirmação por padrão — plano (#88)

Spec: `docs/superpowers/specs/2026-10-16-outbox-com-confirmacao-por-padrao-design.md`

## Tarefas

1. **core** (agente: engineer) — sem dependências.
   - `core/src/main/kotlin/sfv2/core/Event.kt`: `EventType` ganha `MILESTONE_PENDING` e `MILESTONE_CONFIRMED`.

2. **daemon** (agente: engineer) — depende da tarefa 1.
   - `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `Request.MilestoneList`, `Request.MilestoneConfirm` (prova mascarada no `toString`), `Response.MilestonesResult`, comandos `milestone.list` e `milestone.confirm`.
   - `daemon/src/main/kotlin/sfv2/daemon/ExternalMilestones.kt`: consulta `signers[MILESTONE]`; `owner` registra `MILESTONE_PENDING` uma vez por marco por card; `handle` atende lista e confirmação (prova, ator dono, hash, `MILESTONE_CONFIRMED`, escrita, `MILESTONE_WRITTEN`; repetição conforme AC-6/AC-7).
   - `SocketServer.kt`, `CardCommands.kt`, `Main.kt`, `RefineRuntime.kt`: ligação do mesmo `ExternalMilestones` e do `OwnerProof`.
   - `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` (existente): testes atuais passam a declarar `signers.milestone: policy`; ajustar `ValidatePipelineTest.kt` se o construtor mudar.

## Gates
`scope`, `acceptance-intact`, `migration`, `build` após cada tarefa; `green daemon` nos seletores do QA.

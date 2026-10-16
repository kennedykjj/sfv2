# Horário de trabalho e step-up de modelo — plano (#35)

Spec: `docs/superpowers/specs/2026-10-16-horario-de-trabalho-e-step-up-de-modelo-design.md`

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | daemon | engineer | — | `daemon/src/main/kotlin/sfv2/daemon/WorkHours.kt` (AC-1 a AC-4) |
| 2 | daemon | engineer | — | `daemon/src/main/kotlin/sfv2/daemon/ModelStepUp.kt` (AC-5 a AC-10) |

Testes de aceitação (QA, antes das tarefas): `WorkHoursTest.kt` e `ModelStepUpTest.kt` em `daemon/src/test/kotlin/sfv2/daemon/`.

## Notas

- `WorkHours` implementa `WorkSchedule` (já em `Dispatcher.kt`); `from(raw)` devolve `AlwaysWorkTime` quando ausente.
- `ModelStepUp.onEvent` só enfileira e volta (roda sob o lock do `EventRecorder`); a consulta a `store.card` acontece numa thread própria.
- Conjunto de motivos que sobem fica numa constante com o motivo de cada item.
- Fiação em `Main.kt` fica fora: o dispatcher ainda não é criado lá.

# Plano — #159 daemon: limites de custo, preços, horário e habilitação

Spec: `docs/superpowers/specs/2026-10-16-daemon-ligar-limites-de-custo-precos-hor-design.md`. Camada única: `daemon` (agente `engineer`). Tarefas sequenciais.

## Tarefa 1 — DispatcherSettings (daemon)
- Novo `daemon/src/main/kotlin/sfv2/daemon/DispatcherSettings.kt`: `DispatcherSettings` + `resolve(factoryYaml, env)` + `InvalidDispatcherSettingsException(field, message)`. Regras da seção Architecture/Security da spec (enabled, limites `BigDecimal`, `work_hours` via `WorkHours.from`, `unlimited` só por `SFV2_COST_UNLIMITED`, `SFV2_DISPATCHER_ENABLED` vence).
- Novo `ConfiguredDispatchControl.kt`: `LaunchGuard` + `WorkSchedule` sobre provedor de settings, falha fechada, log só na mudança de estado.
- Depende de: nada.

## Tarefa 2 — Ligação (daemon)
- `RefineRuntime.kt`: `PriceTable` de `dataDir/prices.yml`, `ConfiguredDispatchControl` como `launchGuard`/`schedule`, projeto do card via `store.projectOf` com fallback de projeto único (grava `assignProject`), erro com id do card.
- `RefinePipeline.kt`: `onRunFinished` em `finally`.
- `Main.kt`: repassa `env` e `source`; só inicia o dispatcher se permitido.
- Depende de: tarefa 1.

Arquivos de teste existentes: `DispatcherSettingsTest.kt` (novo), `DispatcherTest.kt`, `RunCostTest.kt`, `WorkHoursTest.kt`, `RefinePipelineTest.kt`.

# Configurações — plano (#69)

Spec: `docs/superpowers/specs/2026-10-16-configuracoes-design.md`. Peso `heavy`: três camadas, em ordem (cada uma depende da anterior).

## Contratos fixados (QA escreve os testes contra eles)

`core` (`sfv2.core`, arquivo `FactorySettings.kt`):
- `enum class NotificationType { DECISION_WAITING, RUN_FAILED, FACTORY_STOPPED }`
- `data class WorkHoursSetting(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime, val zone: String)`
- `data class CostSetting(val perCardUsd: BigDecimal?, val perDayUsd: BigDecimal?)`
- `data class NotificationSetting(val enabled: Set<NotificationType>, val quietStart: LocalTime?, val quietEnd: LocalTime?)`
- `data class FactorySettings(val poolSize: Int, val workHours: WorkHoursSetting?, val cost: CostSetting, val notifications: NotificationSetting)` com `FactorySettings.DEFAULT`.
- `data class SettingsIssue(val field: String, val reason: String)`
- `FactorySettingsRules.validate(settings: FactorySettings, costUnlimitedAllowed: Boolean = true): List<SettingsIssue>`: pool 1..64 (campo `poolSize`), `workHours.end` depois de `start` na mesma janela (campo `workHours`; início == fim recusado), fuso por `ZoneId.of` (campo `workHours.zone`), dias não vazios (campo `workHours.days`), limites de custo não negativos e maiores que zero (campos `cost.perCardUsd`, `cost.perDayUsd`); com `costUnlimitedAllowed = false`, `perDayUsd == null` é recusado (campo `cost.perDayUsd`), idem `perCardUsd == null` (campo `cost.perCardUsd`).
- `EventType.SETTINGS_SAVED`.

`daemon` (`sfv2.daemon`):
- `SettingsStore(path: Path, clock: Clock)`: `load(): FactorySettings` (arquivo ausente = `DEFAULT`), `save(settings: FactorySettings)` (escrita atômica, modo 0600); `path` é o `settings.json`.
- `Request.SettingsGet` (cmd `settings.get`) e `Request.SettingsSave(actor, settings, proof)` (cmd `settings.save`); `Response.Settings(settings)` e `Response.Error(message)` cuja mensagem começa com o nome do campo recusado.
- `SettingsCommands(store: SettingsStore, recorder: EventRecorder, cards: CardEventStore, ownerProof: OwnerProof, env: Map<String, String>, clock: Clock, log: DaemonLog)` com `handle(request): Response`. Valida (AC-4, AC-9, AC-10), confere ator dono + prova (AC-5), grava o arquivo e depois grava `SETTINGS_SAVED` num card reservado (`SettingsCommands.SETTINGS_CARD_ID`, coluna INBOX); se o evento falhar restaura o arquivo anterior e responde erro (AC-11). `card.list` esconde o card reservado.
- Próximo ciclo (AC-3): `ConfiguredDispatchControl` recebe `settings: SettingsStore` e lê a cada chamada; `RunPool` aceita `capacity: () -> Int` (construtor secundário com `Int` mantido).

`app` (`sfv2.app`):
- `fun interface SettingsClient`-like: `interface SettingsClient { fun load(): LoadResult; fun save(settings: FactorySettings): SaveResult }` com `sealed interface SaveResult { Saved(settings), Refused(field: String?, message: String), Unreachable }`.
- `data class SettingsDraft(val saved: FactorySettings, val draft: FactorySettings = saved, val error: SaveError? = null)`: `changeCount`, `edit { }`, `discard()`, `issues()`, `save(client)` (valida antes; Refused/Unreachable mantêm o rascunho e preenchem `error`; Saved troca `saved` e `draft`).

## Tarefas

1. **core** (dono: engineer, `core/`): `FactorySettings.kt` (novo), `EventType.SETTINGS_SAVED` em `Event.kt`.
2. **daemon** (dono: engineer, `daemon/`, depois da 1): `SettingsStore.kt`, `SettingsCommands.kt` (novos); `Protocol.kt` (parse/render, JSON das configurações), `SocketServer.kt`, `CardCommands.kt` (esconder card reservado), `ConfiguredDispatchControl.kt`, `RunPool.kt`, `RefineRuntime.kt`, `Main.kt`.
3. **app** (dono: engineer, `app/`, depois da 1): `SettingsDraft.kt`, `SettingsClient.kt` (+ `SocketSettingsClient`), `SettingsScreen.kt` (novos), `Main.kt`, `Module.kt`, `ConnectionScreen.kt`, `build.gradle.kts` (serialization json, se preciso).

Testes: `FactorySettingsTest` (core), `SettingsCommandsTest` (daemon), `SettingsDraftTest` (app), todos novos.

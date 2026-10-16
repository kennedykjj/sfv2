# Previsão de término da fila — plano

Spec: `docs/superpowers/specs/2026-10-16-previsao-de-termino-da-fila-design.md`

1. **Core (teste primeiro):** `QueueForecastTest.kt` com ACs 1 a 3 (soma por coluna restante, divisão pelo pool com teto, dono segura N, fila vazia, sem pool, sem mediana). Depois `QueueForecast.kt` com `QueueForecasts.of`. `./gradlew :core:test`.
2. **Daemon:** teste em `SocketServerTest.kt` para `factory.pulse` com `forecast` (AC-4); `FactoryPulse.kt` calcula com `ColumnTimes.of` dos últimos 7 dias e `QueueForecasts.of`; `Protocol.kt` serializa o campo aditivo. `./gradlew :daemon:test`.
3. **App:** teste em `BoardStateTest.kt` (AC-5: textos, singular/plural, amanhã, desatualizada); `PulseSource.kt` lê `forecast`; `PulseModel.kt` e `Theme.kt` formatam; `PulseStrip.kt` desenha a quarta célula (AC-6). `./gradlew :app:test`.
4. **Verificação:** `./gradlew build`; captura da faixa em desktop (≥1100 px) e estreito (<880 px).

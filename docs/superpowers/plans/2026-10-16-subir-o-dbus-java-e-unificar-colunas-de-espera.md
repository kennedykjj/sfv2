# Plano — dbus-java estável e colunas de espera unificadas (#227)

1. **core** — `Column.kt`: adicionar `OWNER_WAIT_COLUMNS = setOf(SPEC_REVIEW, STAGED)`. `AssistantState.kt`: apagar `waitingColumns` e usar o novo conjunto. Teste: `AssistantStateTest` (estado `WAITING_OWNER` para as duas colunas e só elas).
2. **app** — `BoardState.kt`: `OWNER_DECISION_COLUMNS = OWNER_WAIT_COLUMNS`. `OwnerNotices.kt`: apagar `NOTICE_COLUMNS` e usar o conjunto do `core`. Testes: `BoardStateTest` (conjunto igual ao do `core`) e `OwnerNoticesTest` (aviso só nas colunas de espera).
3. **dependência** — `gradle/libs.versions.toml`: subir `dbus-java` para a última estável com versão fixa; compilar `app` e corrigir só o que `DbusTray.kt` exigir.
4. **verificar** — `./gradlew :core:test :app:test`, depois `./gradlew build`; abrir o app no Linux e confirmar o ícone da bandeja.

# Subir o dbus-java para a estável mais recente e unificar as colunas de espera do dono — spec

**Issue:** #227 · **Branch:** `feat/227-subir-o-dbus-java-e-unificar-colunas-de-espera` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** (1) O `dbus-java` está em `5.1.1` e deve ir para a última versão estável, para a bandeja do Linux não ficar presa a uma versão antiga. (2) O conjunto de colunas em que o card espera o dono (`SPEC_REVIEW`, `STAGED`) está escrito três vezes: `AssistantStates.waitingColumns` no `core`, `OWNER_DECISION_COLUMNS` em `app/.../BoardState.kt` e `NOTICE_COLUMNS` em `app/.../OwnerNotices.kt`. Se uma cópia mudar sozinha, o estado do assistente, o destaque do board e a notificação passam a discordar.
**Audience:** o dono que usa o app desktop e a bandeja; quem mantém o código.
**Acceptance criteria**
- **AC-1** — Given o catálogo de versões, when o `dbus-java` é atualizado, then `gradle/libs.versions.toml` fixa a última versão estável (sem RC, beta ou snapshot) para `dbus-java-core` e `dbus-java-transport-native-unixsocket`, e o `./gradlew build` passa sem mudar o comportamento da bandeja.
- **AC-2** — Given o `core`, when qualquer parte do sistema pergunta quais colunas esperam o dono, then existe uma única definição pública (`SPEC_REVIEW` e `STAGED`) e `AssistantStates.derive` devolve `WAITING_OWNER` para um card em cada uma delas.
- **AC-3** — Given o `app`, when o board marca um card como aguardando o dono, then `isWaitingForOwner` e `OWNER_DECISION_COLUMNS` usam a definição do `core`, sem lista própria.
- **AC-4** — Given o `app`, when `OwnerNotices` decide quais cards geram aviso, then usa a mesma definição do `core`, e `NOTICE_COLUMNS` deixa de existir.
**Out of scope:** mudar quais colunas esperam o dono; mudar a interface da bandeja; trocar de biblioteca D-Bus; atualizar outras dependências.
**Product fit:** o assistente, o board e a bandeja precisam concordar sobre "esperando o dono"; uma fonte só evita divergência.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- `gradle/libs.versions.toml`: `dbus-java` sobe para a última estável (versão fixa). Quem implementar confere no Maven Central qual é a última sem sufixo `-RC`/`-beta`/`-SNAPSHOT` e registra o número no PR.
- `app/src/main/kotlin/sfv2/app/DbusTray.kt`: ajustar só o que a nova versão quebrar na compilação (imports, anotações, `DBusSignal`); sem mudar o contrato StatusNotifierItem.
- `core/src/main/kotlin/sfv2/core/Column.kt`: define o conjunto público único `OWNER_WAIT_COLUMNS` (`SPEC_REVIEW`, `STAGED`); constante pura, sem IO.
- `core/src/main/kotlin/sfv2/core/AssistantState.kt`: remove `waitingColumns` privado e usa o conjunto do `core`.
- `app/src/main/kotlin/sfv2/app/BoardState.kt`: `OWNER_DECISION_COLUMNS` passa a apontar para o conjunto do `core` (mantém o nome público, pois é usado em teste e em `isWaitingForOwner`).
- `app/src/main/kotlin/sfv2/app/OwnerNotices.kt`: remove `NOTICE_COLUMNS` e usa o conjunto do `core`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | manual | `./gradlew build` verde; abrir o app numa sessão Linux e ver o ícone da bandeja registrado |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/AssistantStateTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardStateTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |

## Security
_Cybersec_

Atualização de dependência de terceiros (cadeia de suprimentos): a versão entra fixa em `gradle/libs.versions.toml`, nunca por faixa; só a coordenada `com.github.hypfvieh` já usada. Conferir no PR as notas de versão da biblioteca por mudança incompatível ou de segurança. Sem segredo, token ou dado externo envolvido; o código D-Bus continua só na sessão local do dono.

## Rollback
Reverter o commit: a versão volta a `5.1.1` e as três listas de colunas voltam a existir. Sem migração nem dado gravado.

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Conjunto único mora no `core`: é regra de negócio pura e o `core` já a usa; `app` e `daemon` já dependem dele.
- `OWNER_DECISION_COLUMNS` mantém o nome no `app` como alias do `core`, para não quebrar `BoardStateTest` nem `isWaitingForOwner`.
- AC-1 é `manual`: não há teste de unidade da bandeja D-Bus (precisa de sessão real); o build compilando `DbusTray.kt` é o sinal automático.
- Sem arquivo de teste novo: as suítes `AssistantStateTest`, `BoardStateTest` e `OwnerNoticesTest` já cobrem os pontos.
- Peso `heavy`: duas camadas (`core` e `app`) e mudança de dependência de terceiros em `## Security`.

## Open questions
- none

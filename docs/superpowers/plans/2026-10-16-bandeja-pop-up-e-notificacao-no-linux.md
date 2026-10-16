# Plano — Bandeja, pop-up e notificação no Linux (#70)

Spec: `docs/superpowers/specs/2026-10-16-bandeja-pop-up-e-notificacao-no-linux-design.md`. Camadas: `app` (T1–T3) e `cli` (T4). Tarefas em ordem; cada uma compila e passa sozinha.

## Contratos (fixados aqui para QA e engenheiro)

Pacote `sfv2.app`, arquivo `app/src/main/kotlin/sfv2/app/OwnerNotices.kt` (puro: sem IO, tempo por parâmetro):

- `data class WaitingCard(val id: String, val column: Column, val since: Instant, val externalId: String?)`; `column` é `SPEC_REVIEW` ou `STAGED`.
- `data class WaitingCards(...)` imutável, `WaitingCards.EMPTY`:
  - `fun withSnapshot(cards: List<BoardCard>, now: Instant): WaitingCards`: conta os cards cuja coluna real é `SPEC_REVIEW`/`STAGED` (AC-10).
  - `fun withEvent(event: BoardEvent, fetched: BoardCard?, now: Instant): WaitingCards`: `CARD_MOVED` e `OWNER_APPROVED` usam `payload["to"]` (AC-9); `CARD_CREATED` usa a coluna de `fetched` (o `card.show`; `null` = não conta) e guarda `payload["externalId"]` como `externalId` (AC-10, AC-11); outros tipos não mudam nada. Card que já espera mantém o `since` ao mudar de uma coluna de espera para outra; card que sai de espera sai do conjunto.
  - `val ordered: List<WaitingCard>` (mais antigo primeiro, desempate por id), `val count: Int`, `fun contains(id: String): Boolean`.
- `enum class NoticeLanguage { PT_BR, EN }`.
- `object NoticeText`: `TITLE = "Shipfloor"`; `fun label(card: WaitingCard): String` (`#<externalId>` quando há referência; senão os 8 primeiros caracteres hexadecimais do id, nunca o título — AC-11); `fun body(cards: List<WaitingCard>, language: NoticeLanguage): String`; `fun tooltip(count: Int, language: NoticeLanguage): String`. Frases da UX da spec: um card `Card #70 espera sua decisão: Spec Review.` (`Staged.`; en `Card #70 is waiting for your decision: Spec Review.`); vários `3 cards esperam sua decisão: #70, #72, #75.` (en `3 cards are waiting for your decision: ...`); mais de 5 cards mostram os 5 primeiros ids e `e mais N` (en `and N more`), N = total − 5. Tooltip: `Shipfloor` com 0; `Shipfloor · N esperando você` (en `N waiting for you`).
- `sealed interface NoticeDecision`: `data object None`; `data object Clear` (ícone neutro, sem notificação); `data class Show(val cards: List<WaitingCard>, val notify: Boolean)` (`cards` = tudo que espera, ordenado; `notify` falso só atualiza ícone e tooltip).
- `class OwnerNoticePolicy { fun decide(waiting: WaitingCards, quiet: Boolean): NoticeDecision }` (com estado: lembra o que já foi avisado):
  - fora do silêncio: vazio → `Clear` se algo estava mostrado, senão `None`; há card ainda não avisado → `Show(todos, notify = true)`; só saíram cards (conjunto menor, nenhum novo) → `Show(todos, notify = false)`; nada mudou → `None`;
  - em silêncio: `Clear` na primeira chamada, depois `None`; esquece o que foi avisado, de modo que na primeira chamada fora do silêncio o que ainda espera sai em um único `Show(todos, notify = true)` (AC-2, AC-3); card resolvido no silêncio não entra.
- `object NoticeClick { fun target(cited: List<String>, waiting: WaitingCards): String? }`: o primeiro id de `cited` que ainda espera; `null` (abrir o quadro) quando nenhum (AC-8). Clique no ícone usa `waiting.ordered.firstOrNull()?.id`.

`app/src/main/kotlin/sfv2/app/OwnerNoticeShell.kt`:

- `interface TrayPort { fun show(waiting: Int); fun clear(); fun onActivated(listener: () -> Unit) }`.
- `interface NotificationPort { fun notify(title: String, body: String, replacesId: Int): Int; fun onActivated(listener: (notificationId: Int) -> Unit) }` (0 = nova; o retorno é o id a reusar como `replacesId`).
- `class OwnerNoticeShell(tray: TrayPort, notifications: NotificationPort, clock: Clock, zone: ZoneId, language: NoticeLanguage, log: (String, Map<String, String>) -> Unit, open: (String?) -> Unit)`:
  - `fun onSnapshot(cards: List<BoardCard>)`, `fun onEvent(event: BoardEvent, fetched: BoardCard?)`, `fun tick()` (reavalia o silêncio sem evento novo), cada um termina chamando a política e as portas;
  - `quiet` = `AssistantStates.derive(emptyList(), clock.instant(), AssistantStateRules(zone = zone)) == AssistantState.NIGHT`;
  - `Show`: `tray.show(count)`; se `notify`, `notifications.notify(NoticeText.TITLE, body, replacesId = id da última notificação)`; `Clear`: `tray.clear()`;
  - falha de uma porta (qualquer `RuntimeException`) é capturada, vai ao `log` (`key=value`, com `card=<id>` de cada card envolvido e o motivo, nunca texto de card) e a outra porta segue (AC-7);
  - clique na notificação → `open(NoticeClick.target(ids citados nela, waiting))`; clique no ícone → `open(primeiro em espera)`; ambos sem erro quando o card já foi resolvido.
- `BoardMonitor` ganha o parâmetro final `observer: (BoardEvent, BoardCard?) -> Unit = { _, _ -> }` (default mantém os testes atuais) chamado em `apply` com o evento e o `fetched`; e `snapshotObserver: (List<BoardCard>) -> Unit = {}` chamado em `loadSnapshot`. Texto de evento nunca é logado.

## Tarefas

### T1 — `app`: lógica pura de aviso (agent: engineer)
Arquivos: `app/src/main/kotlin/sfv2/app/OwnerNotices.kt`. Implementa `WaitingCard`, `WaitingCards`, `NoticeLanguage`, `NoticeText`, `NoticeDecision`, `OwnerNoticePolicy`, `NoticeClick` como acima. Cobre AC-1/2/3/5/8/9/10/11 (lado puro). Reaproveita `BoardCard`, `BoardEvent` de `BoardState.kt`/`BoardSource.kt`. Sem dependência nova.

### T2 — `app`: shell e ganchos do monitor (agent: engineer; depende de T1)
Arquivos: `app/src/main/kotlin/sfv2/app/OwnerNoticeShell.kt`, `app/src/main/kotlin/sfv2/app/BoardMonitor.kt` (só os dois parâmetros opcionais). Cobre AC-1/2/3/5/7/8 (portas fake nos testes).

### T3 — `app`: adaptadores D-Bus, ícones e Main (agent: engineer; depende de T2)
Arquivos: `app/src/main/kotlin/sfv2/app/DbusTray.kt` (StatusNotifierItem + `com.canonical.dbusmenu`, menu `Abrir Shipfloor` / `N esperando você` desabilitado e escondido com 0 / `Sair`), `DbusNotifier.kt` (`org.freedesktop.Notifications.Notify` com `replaces_id`, ação `default`, sinal `ActionInvoked`, urgência normal), PNGs transparentes 22/24/48 px em `app/src/main/resources/` (neutro e esperando, cores de `DESIGN.md`; esperando com ponto preenchido no canto), `Main.kt` (liga o shell ao monitor e a um `tick()` por minuto; clique chama `window.toFront()` e navega via `NoticeClick`; falha ao abrir D-Bus vira log e o app abre normalmente), `app/build.gradle.kts` e `gradle/libs.versions.toml` (`dbus-java-core` + `dbus-java-transport-native-unixsocket`, versão fixa: a estável mais recente publicada há mais de duas semanas). Adaptadores sem lógica, sem teste unitário (AC-4 manual). Cobre AC-4 e a ligação de AC-7/AC-8.

### T4 — `cli`: aviso do AppIndicator no doctor (agent: engineer; independente)
Arquivos: `cli/src/main/kotlin/sfv2/cli/DoctorCommand.kt`, `cli/src/main/kotlin/sfv2/cli/CardCommands.kt` (`CliEnvironment`), `cli/src/main/kotlin/sfv2/cli/Main.kt` (`runCli`). `runCli` ganha o último parâmetro `runCommand: (List<String>) -> String? = ::runProcess` (stdout do comando, `null` se não existe/falha) e `CliEnvironment` o recebe. Item `appindicator`: só quando `XDG_CURRENT_DESKTOP` contém `GNOME` e `runCommand(listOf("gnome-extensions", "list", "--enabled"))` não lista `appindicatorsupport@…`; linha `AVISO  A bandeja não vai aparecer: a extensão AppIndicator do GNOME está desligada. Ligue com: gnome-extensions enable <uuid>` (uuid instalado, `gnome-extensions list` sem filtro, senão `appindicatorsupport@rgcjonas.gmail.com`); fora do GNOME ou sem o comando (`null`), nenhuma linha; com a extensão ligada, nenhuma linha; o código de saída não muda. Cobre AC-6.

## Verificação
`./gradlew :app:test`, `./gradlew :cli:test`, `./gradlew build`. Manual (AC-4): GNOME com a extensão ligada, ícone transparente e menu.

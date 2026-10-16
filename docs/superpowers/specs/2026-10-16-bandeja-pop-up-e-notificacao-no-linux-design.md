# Bandeja, pop-up e notificação no Linux — spec

**Issue:** #70 · **Branch:** `feat/70-bandeja-pop-up-e-notificacao-no-linux` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** O dono não está olhando a fábrica o tempo todo; quando um card passa a esperar a decisão dele (Spec Review ou Staged), ninguém o avisa e a fábrica fica parada. O app precisa chamá-lo, no Linux, sem incomodar à noite.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, igualmente, o desenvolvedor dentro de uma empresa), que deixa a fábrica trabalhando e só aparece onde a decisão é dele.
**Acceptance criteria**
- **AC-1** — Dado um card que passa a esperar o dono (estado WAITING_OWNER), quando o app recebe a mudança do daemon, então aparece um aviso na bandeja e uma notificação do desktop.
- **AC-2** — Dado o horário de silêncio (a janela noturna do assistente, hoje 22h-7h), quando um card passa a esperar o dono, então nada aparece na bandeja nem como notificação até o fim da janela.
- **AC-3** — Dado que cards passaram a esperar o dono durante o silêncio, quando a janela termina, então o dono vê o aviso do que ainda espera por ele (um só, sem repetir um por card já resolvido).
- **AC-4** — Dado o app em execução no GNOME, quando ele inicia, então o ícone da bandeja é registrado como StatusNotifierItem via D-Bus (não pelo Tray do AWT) e aparece com fundo transparente.
- **AC-5** — Dado um aviso a mostrar, quando a notificação é emitida, então ela sai por `org.freedesktop.Notifications` e contém só a frase do estado e os ids envolvidos, nunca texto de card, issue ou spec.
- **AC-6** — Dado que a extensão AppIndicator do GNOME está desligada, quando o dono roda `sfv2 doctor`, então ele mostra um aviso dizendo que a bandeja não aparecerá e como ligar a extensão; com a extensão ligada, o aviso não aparece.
- **AC-7** — Dado que a bandeja ou o serviço de notificações do D-Bus não está disponível, quando um card passa a esperar o dono, então o app não falha, usa o canal que restou e registra o motivo em log com o id do card.
- **AC-9** — Dado um card aprovado pelo dono em Spec Review (o daemon grava `OWNER_APPROVED` com `payload.to`, não `CARD_MOVED`), quando a aprovação chega, então o card sai do conjunto de espera, o contador e o tooltip caem e nenhuma notificação nova nasce por ele.
- **AC-10** — Dado um card criado direto em Spec Review ou em Staged (importado do board da V0 ou reconstruído do forge, cujo `CARD_CREATED` só traz o título), quando o app inicia ou o evento chega, então o card conta como em espera pela coluna real (snapshot `card.list` ou `card.show`), não por suposição.
- **AC-11** — Dado um card sem referência externa (id UUID), quando a notificação o cita, então o rótulo é os 8 primeiros caracteres hexadecimais do id; com referência, `#<número>`; nunca o título.
- **AC-8** — Dado o dono que clica na notificação ou no ícone da bandeja, quando o app está aberto ou fechado na janela, então a janela do app abre no card que espera por ele.
**Out of scope:** Skins e animação do pop-up (outras issues da fase 6/7); horário de silêncio configurável pelo dono (usa a janela noturna existente); macOS e Windows (fase 8); outros tipos de aviso além de "espera o dono"; suporte a desktops sem StatusNotifierItem além do aviso do doctor; Tray do Compose/AWT.
**Product fit:** Serve ao princípio de que o dono aparece só onde a decisão é dele e a fábrica trabalha fora do horário dele, sempre com modo silencioso (Brand Commitments). Não contradiz o posicionamento: nada sai da máquina e a notificação não carrega texto externo não confiável.
**Touches UI:** yes

## UX
_UX/UI Designer_

- **Ícone (único elemento visual nosso):** a marca da Shipfloor (rota + cartão, a mesma do favicon do dashboard), PNG com fundo transparente em 22/24/48 px, sem emoji nem fonte de ícones. Dois estados, ambos reaproveitando as cores do `DESIGN.md`: neutro (rota em `muted`, cartão em `text`, nada espera) e esperando (traço em `sand`, o acento de "espera o dono"); o estado de dispatcher parado fica de fora desta issue. O estado muda só quando a contagem de cards em WAITING_OWNER vai de 0 a >0 ou volta a 0; o ícone usa só formato, não depende só de cor (esperando traz um ponto preenchido no canto), para funcionar em painel claro e escuro.
- **Tooltip do ícone:** neutro `Shipfloor`; esperando `Shipfloor · N esperando você` (en: `N waiting for you`). Só contagem, nunca título de card.
- **Clique no ícone (AC-8):** esquerdo abre/foca a janela do app no card mais antigo em espera (com a janela já aberta, só leva o foco a ele); sem card em espera, abre a janela no quadro. Não alterna nem fecha a janela.
- **Menu (clique direito/menu do SNI), 3 itens, sem submenu:** `Abrir Shipfloor` (en: `Open Shipfloor`), `N esperando você` (en: `N waiting for you`; desabilitado, só informa, escondido quando N = 0), `Sair` (en: `Quit`). Rótulos em português, ou em inglês quando a máquina estiver em inglês.
- **Notificação (AC-1, AC-5):** título `Shipfloor`; corpo, uma frase de estado + ids: `Card #70 espera sua decisão: Spec Review.` / `Card #70 espera sua decisão: Staged.` (en: `Card #70 is waiting for your decision: Spec Review.`). Os nomes das colunas ficam em inglês em todo idioma, como no dashboard. Sem texto de card, issue ou spec, sem emoji, sem cor própria, sem imagem além do ícone do app; urgência `normal`, sem som próprio.
- **Vários cards:** a partir do segundo card esperando, a notificação do mais novo substitui a anterior (mesmo `replaces_id`), em vez de empilhar: `3 cards esperam sua decisão: #70, #72, #75.` (en: `3 cards are waiting for your decision: #70, #72, #75.`); mais de 5 ids vira `#70, #72, #75 e mais 4` (en: `and 4 more`).
- **Clique na notificação (AC-8):** ação padrão `default` abre a janela no card citado; se o card já foi resolvido, abre no quadro sem erro. Sem botões de ação extras.
- **Silêncio (AC-2, AC-3):** durante 22h-7h nada aparece: o ícone fica neutro e sem notificação. No fim da janela, um só aviso: ícone em "esperando" e uma notificação com o resumo do que ainda espera (mesma frase de vários cards, ou a de um card se for um só); card resolvido no silêncio não entra na conta.
- **Estados de falha (AC-7):** sem bandeja, só a notificação sai; sem serviço de notificações, só o ícone muda. Nenhum dos dois mostra erro ao dono (o motivo vai para o log com o id do card); o app abre normalmente.
- **Aviso do `sfv2 doctor` (AC-6):** linha de aviso no formato dos demais itens do doctor, só quando a extensão AppIndicator está desligada: `AVISO  A bandeja não vai aparecer: a extensão AppIndicator do GNOME está desligada. Ligue com: gnome-extensions enable appindicatorsupport@rgcjonas.gmail.com` (en: `WARN  The tray icon will not appear: the GNOME AppIndicator extension is off. Turn it on with: ...`). Com a extensão ligada, a linha não aparece, nem como "ok". É aviso, não falha: o código de saída do doctor não muda.
- **Tela pequena:** a janela do app aberta pelo clique segue o layout já existente do app; esta issue não cria tela nova nem componente novo (o menu e a notificação são nativos do GNOME).

## Architecture
_Tech Lead_

**Contratos.** Nenhum endpoint nem evento novo: o app lê o stream existente `{"cmd":"events.subscribe","after":<seq>}` do socket do daemon (`$XDG_RUNTIME_DIR/sfv2/daemon.sock`) e usa `CARD_MOVED` (`payload.to`, `card`, `at`). Um card "espera o dono" quando está em `SPEC_REVIEW` ou `STAGED` (as mesmas `waitingColumns` de `AssistantStates`). A coluna vem do snapshot `card.list` ao iniciar e depois dos eventos que a mudam: `CARD_MOVED` e `OWNER_APPROVED` (a aprovação do dono em Spec Review grava só `OWNER_APPROVED`), ambos com `payload.to`; `CARD_CREATED` só traz o título, então o app consulta `card.show` para a coluna real. Deduzir só do último `CARD_MOVED` deixaria o card aprovado contando como em espera e não contaria os cards criados direto numa coluna de espera (AC-9, AC-10). Os rótulos de card nas frases são `#<número da issue>` ou os 8 primeiros caracteres hexadecimais do UUID, como no #74 (AC-11).

**Camada `app`** (`app/src/main/kotlin/sfv2/app/`):
- `OwnerNotices.kt` (puro, sem IO, tempo por parâmetro): `WaitingCards` (reduz eventos `CARD_MOVED` ao conjunto de cards em espera, com coluna e instante); `OwnerNoticePolicy` (recebe o conjunto, `quiet: Boolean` e `now`; devolve `NoticeDecision`: `None`, `Show(cards)` ou `Clear`; lembra os cards já avisados, não repete, e no fim do silêncio emite um só `Show` com o que ainda espera); `NoticeText` (título `Shipfloor`, corpo e tooltip da UX, pt-BR/en, ids limitados a 5 + "e mais N"); `NoticeClick` (card citado ainda em espera → abre nele, senão o quadro).
- `OwnerNoticeShell.kt`: liga as peças. Interfaces `TrayPort` (`show(waiting: Int)`, `clear()`, cliques) e `NotificationPort` (`notify(title, body, replacesId): Int`, cliques); `quiet` vem de `AssistantStates.derive(...) == NIGHT` com as `AssistantStateRules` padrão (sem tocar no `core`). Falha de uma porta é capturada, vai ao log `key=value` com o id do card e a outra porta segue (AC-7).
- Sem `EventFeed.kt` próprio: o app reaproveita o `BoardSource`/`BoardMonitor` do #64 (snapshot `card.list`, `fetchCard` e `subscribe` com retomada por `after`), em vez de uma segunda implementação do mesmo cliente de socket; texto do evento nunca é logado, só ids. Por isso o #70 fica bloqueado pelo #64 (e pelo #66, para o clique abrir a visão do card em vez de cair só no quadro).
- `DbusTray.kt`: StatusNotifierItem (`org.kde.StatusNotifierItem` + `com.canonical.dbusmenu`, registrado no `org.kde.StatusNotifierWatcher`) com dois ícones PNG transparentes em `app/src/main/resources/` (22/24/48 px); `DbusNotifier.kt`: `org.freedesktop.Notifications.Notify` com `replaces_id`, ação `default` e sinal `ActionInvoked`. São adaptadores finos sem lógica, verificados à mão (AC-4).
- `Main.kt`: inicia o feed e o shell; clique chama `window.toFront()` e leva o app ao card pedido (a navegação em si é `NoticeClick`).
- `app/build.gradle.kts` e `gradle/libs.versions.toml`: `dbus-java` (`dbus-java-core` + `dbus-java-transport-native-unixsocket`) com versão fixa: a estável mais recente publicada há mais de duas semanas, escolhida e fixada no catálogo (nunca faixa nem `latest`); a camada `app` é dona da mudança, o arquivo do catálogo não tem dono no config.

**Camada `cli`:** `DoctorCommand.kt` ganha o item `appindicator`: só avisa quando a sessão é GNOME (`XDG_CURRENT_DESKTOP` contém `GNOME`) e `gnome-extensions list --enabled` não lista uma extensão `appindicatorsupport@…`; fora do GNOME ou sem `gnome-extensions` no PATH não mostra nada. O aviso é `WARN`, não falha o comando (exit code intacto). A execução do comando entra por uma função injetada em `CliEnvironment`/`diagnose` (padrão: `ProcessBuilder`), para o teste usar um fake.

**Migração.** Nenhuma (sem dado persistido; o app guarda o conjunto em memória e recarrega por `after` ao reconectar). **Rollback:** reverter o PR.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-4 | manual | verificação à mão no GNOME com a extensão ligada: ícone transparente e menu |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-6 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-8 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-9 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-10 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |
| AC-11 | app | `app/src/test/kotlin/sfv2/app/OwnerNoticesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo `OwnerNoticesTest.kt`: nenhuma suíte do `app` cobre aviso ao dono (`PopupSkinTest` cobre a animação, `ConnectionMonitorTest` a conexão).
- O app usa o stream `events.subscribe` que já existe: evita comando novo no daemon e nenhuma camada extra entra.
- Coluna de espera é `Staged`, não `Ready for Prod`: segue `AssistantStates.waitingColumns`; corrigi o texto do PO e do UX.
- Silêncio = janela noturna do assistente via `AssistantStates.derive(...) == NIGHT`: reaproveita a regra sem tornar `isNight` público no `core`; configurar a janela fica para a tela de Configurações (#69).
- Teste dos adaptadores D-Bus não entra: o comportamento (quando avisar, texto, clique, falha) é puro e coberto por fakes das portas; o registro real do SNI é AC manual.
- `dbus-java` por ser a opção JVM madura para SNI e Notifications; versão a fixar pelo engenheiro na hora da implementação, conferindo a última estável.
- UUID da extensão no aviso do doctor: o doctor casa qualquer `appindicatorsupport@*` e imprime o comando com o UUID que encontrou instalado; sem nenhuma, usa `appindicatorsupport@rgcjonas.gmail.com`.
- O estado "dispatcher parado" do ícone fica fora (não está nos ACs).
- Peso `heavy`: duas camadas (app e cli).

## Open questions
- none

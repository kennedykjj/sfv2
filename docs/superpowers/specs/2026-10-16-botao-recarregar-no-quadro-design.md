# Botão Recarregar no quadro (ressincronizar com o daemon) — spec

**Issue:** #253 · **Branch:** `feat/253-botao-recarregar-no-quadro` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** o quadro só lê o snapshot do daemon uma vez; depois disso depende do stream de eventos. Se um evento se perde ou o quadro fica fora de passo, o dono não tem como forçar uma releitura sem fechar e reabrir o app.
**Audience:** o dono, ao operar o quadro no app desktop.
**Acceptance criteria**
- **AC-1** — Given o quadro conectado com snapshot já aplicado, when o dono aciona Recarregar, then o monitor lê o snapshot de novo e o quadro passa a refletir exatamente os cards devolvidos (card que saiu do daemon some, card novo aparece, coluna alterada é corrigida).
- **AC-2** — Given um evento que chega do stream enquanto o snapshot de recarga está sendo lido, when a recarga termina, then o evento é aplicado depois do snapshot e o resultado final mostra o estado mais recente do card (sem perder nem duplicar).
- **AC-3** — Given que a leitura do snapshot falha com `IOException`, when o dono aciona Recarregar, then o quadro anterior é mantido sem alteração, os eventos retidos são aplicados e a falha é sinalizada ao dono com mensagem que diz o que aconteceu e como resolver.
- **AC-4** — Given que há uma recarga em andamento, when o dono aciona Recarregar de novo, then nenhuma segunda leitura de snapshot é iniciada, e o botão fica desabilitado com indicação de progresso até a recarga terminar.
- **AC-5** — Given o app sem conexão com o daemon, when o quadro é exibido, then o botão Recarregar fica desabilitado; com conexão, fica visível na barra do quadro ao lado de Configurações, acessível por teclado e com rótulo textual.
**Out of scope:** recarga automática periódica; recarregar outras telas (feed, detalhe do card); mudar o protocolo do socket.
**Product fit:** dá ao dono uma saída local e segura para quadro defasado, sem tocar no banco nem exigir reinício.
**Touches UI:** yes

## UX
_UX/UI Designer_

Botão de texto "Recarregar" na linha de ações do quadro, à esquerda de Configurações, com os tokens existentes de `DESIGN.md` (sem cor ou componente novo). Durante a recarga: rótulo "Recarregando…" e botão desabilitado. Falha: linha de aviso descartável, no mesmo componente de `NoticeLine`, com texto "Não foi possível ler o quadro do daemon. Confira se o daemon está ativo e tente de novo."

## Architecture
_Tech Lead_

- `app/src/main/kotlin/sfv2/app/BoardMonitor.kt`: novo `suspend fun resync(): ResyncResult` (enum/sealed `Reloaded`, `Failed`, `Busy`). Reaproveita `loadSnapshot()`: abre `waiting` para reter eventos, lê o snapshot, aplica com `applySnapshot` e drena os retidos; em falha restaura o quadro e drena. Uma recarga por vez, controlada sob `lock`. Não reinicia a subscrição.
- `app/src/main/kotlin/sfv2/app/BoardScreen.kt`: parâmetro `onReload` e estado de recarga; botão ao lado de `SettingsButton`.
- `app/src/main/kotlin/sfv2/app/Main.kt`: liga `onReload` a `monitor.resync()` num escopo de coroutine e expõe o aviso de falha.

Sem mudança no daemon, no banco, no socket nem em ferramentas MCP; o app segue só lendo pelo `BoardSource`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/BoardMonitorTest.kt` |
| AC-5 | manual | manual |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- A regra de recarga fica no `BoardMonitor`, não na tela: é testável sem UI e reaproveita a lógica de buffer de eventos.
- Recarga não reabre a subscrição: o stream continua válido e `lastSeq` é preservado, evitando duplicar eventos.
- AC-5 é `manual`: a visibilidade e o estado do botão são Compose, sem suíte de UI no módulo.
- Peso `standard`: uma camada (`app`), 3 caminhos, mas `Touches UI` é `yes`.
- O corpo da issue #253 não está disponível nas ferramentas; os critérios partem do título e do código atual.

## Open questions
- none

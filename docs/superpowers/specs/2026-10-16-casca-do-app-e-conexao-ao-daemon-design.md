# Casca do app e conexão ao daemon — spec

**Issue:** #63 · **Branch:** `feat/63-casca-do-app-e-conexao-ao-daemon` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
**Problema:** o app desktop precisa existir e falar com o daemon antes de qualquer tela (board, spec review, entrega). Sem uma casca que conecta e mostra o estado da conexão, as telas seguintes não têm base.

**Audiência:** o desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de uma empresa), que abre o app numa máquina Linux onde o daemon deve estar sempre ligado.

**Critérios de aceitação**
- AC-1: Dado o daemon rodando, quando o app abre, então a janela mostra o estado "conectado" após o `ping` ao socket do daemon ter sucesso.
- AC-2: Dado o app aberto e conectado, quando o daemon para, então a janela passa a mostrar "desconectado" sem fechar nem travar.
- AC-3: Dado o app aberto e desconectado, quando o daemon volta, então a janela volta a mostrar "conectado" sem o dono reabrir o app.
- AC-4: Dado o daemon ausente e o serviço `sfv2.service` instalado, quando o app abre, então ele inicia o serviço e mostra "conectado" ao daemon responder.
- AC-5: Dado o daemon ausente e o serviço não instalado (ou a partida falha ou estoura o tempo), quando o app abre, então a janela explica o motivo em texto claro e indica o próximo passo (por exemplo `sfv2 service install`).
- AC-6: Dado qualquer estado de conexão, então a janela é opaca, abre no Linux (XWayland) e não exibe o texto de issue, spec ou payload.

**Fora de escopo**
- Telas de board, spec, entrega, custo e o assistente (fases seguintes).
- Instalar o serviço pelo app; ele só orienta o uso de `sfv2 service install`.
- Janela transparente e Wayland nativo (spike S0-07); macOS e Windows (fase 8).
- Qualquer escrita de estado pelo app: ele só faz `ping`.

**Encaixe no produto:** serve o contexto operacional (app é cliente do daemon, único escritor, por socket local; a fábrica segue funcionando com o app fechado). Não contradiz posicionamento nem o que está fora de escopo.

**Toca UI:** sim

## UX
- **Tela:** uma janela opaca (fundo `ground`), com um painel central (`panel`, borda `edge`, raio 16px, largura máx. 480px) com título "sfv2", um ponto de estado, a frase de estado e, só quando desconectado, o motivo e o próximo passo. Sem barra, menu, abas nem outros controles; nada de texto de issue, spec ou payload.
- **Tokens e tipografia:** IBM Plex Sans; título em Headline (17px/600), frase de estado em Body (15px), motivo e passo em 14px `soft`; o comando em IBM Plex Mono 12px, num bloco `ground` com raio 4px. Sem componente novo: reaproveita panel, ponto de estado e bloco de código do DESIGN.md.
- **Cor do ponto:** conectando `muted`; conectado `ok`; desconectado `warn` (problema de infraestrutura, nunca `bad` nem o acento `sand`). O estado também vai por texto, nunca só por cor.
- **Fluxo ao abrir:** a janela abre já em "Conectando" (sem tela vazia); o app dá `ping`; sucesso leva a "Conectado". Sem resposta, tenta iniciar `sfv2.service` e continua em "Conectando" até responder (AC-4); falha ou tempo esgotado leva a "Desconectado" com motivo e passo (AC-5).
- **Em execução:** o app repete o `ping` em intervalo curto. Conectado que perde o daemon vira "Desconectado" sem fechar a janela (AC-2); desconectado que recupera volta a "Conectado" sozinho, sem botão (AC-3). Sem botão "tentar de novo": a janela se atualiza sozinha.
- **Textos (pt-BR):**
  - Conectando: "Conectando ao daemon…"
  - Conectado: "Conectado ao daemon"
  - Desconectado: "Desconectado do daemon"
  - Motivo, serviço ausente: "O serviço sfv2 não está instalado." Passo: "Instale com" + `sfv2 service install`.
  - Motivo, partida falhou: "O serviço sfv2 não iniciou." Passo: "Veja o estado com" + `sfv2 doctor`.
  - Motivo, estourou o tempo: "O daemon não respondeu a tempo." Passo: "Veja o estado com" + `sfv2 doctor`.
  - Motivo, daemon parou depois de conectado: "O daemon parou de responder." Passo: "A janela volta sozinha quando ele voltar."
- **Idioma:** o projeto tem só pt-BR; sem camada de i18n nesta issue. Os textos ficam num único lugar do código para a troca futura.
- **Acessibilidade:** a frase de estado fica numa região `role="status"` (`aria-live="polite"`), então a troca de estado é anunciada; o contraste dos textos é o dos tokens (4.5:1); o comando é texto selecionável para copiar. Sem animação obrigatória; o ponto de "Conectando" pulsa só sem `prefers-reduced-motion: reduce`.
- **Janela pequena:** abre em 640x400 e o mínimo é 360x240; o painel ocupa a largura menos 16px de margem e o comando quebra linha em vez de rolar na horizontal.

## Architecture
_Tech Lead_

**Contratos:** nenhum contrato novo no daemon. O app só envia `{"cmd":"ping"}` (uma linha JSON por conexão) ao socket `$XDG_RUNTIME_DIR/sfv2/daemon.sock` e espera `ok:true`. Não lê o banco nem escreve estado.

**Camadas e módulos:** só `app` (depende só de `core`, como a tabela de `build-logic`). Arquivos:
- `gradle/libs.versions.toml`: plugin e dependências do Compose Desktop, versões fixas (Compose Multiplatform 1.12.1, a do spike).
- `app/build.gradle.kts`: plugins do Compose, `application` com `mainClass` e `applicationName = "sfv2-app"`.
- `app/src/main/kotlin/sfv2/app/ConnectionState.kt`: `sealed interface ConnectionState` (`Connecting`, `Connected`, `Disconnected(reason: DisconnectReason)`) e o enum `DisconnectReason` (`SERVICE_NOT_INSTALLED`, `START_FAILED`, `START_TIMED_OUT`, `NOT_RESPONDING`, `NO_RUNTIME_DIR`).
- `app/src/main/kotlin/sfv2/app/DaemonProbe.kt`: port `fun interface DaemonProbe { fun ping(): Boolean }` e `DaemonStarter { fun start(): StartOutcome }`, mais as implementações reais (socket Unix com timeout curto; `systemctl --user start sfv2.service`, saída `Started`, `NotInstalled` ou `Failed`).
- `app/src/main/kotlin/sfv2/app/ConnectionMonitor.kt`: lógica sem UI. Recebe `DaemonProbe`, `DaemonStarter` e `Clock`; ao abrir tenta o ping, se falhar tenta iniciar o serviço uma vez e repete o ping até o prazo (10 s); depois, ping periódico a cada 3 s, expondo `StateFlow<ConnectionState>`. Tempo e laços controlados por parâmetro (scheduler injetado), sem `Thread.sleep`.
- `app/src/main/kotlin/sfv2/app/ConnectionScreen.kt` e `Main.kt`: janela opaca Compose (640x400, mínimo 360x240) que renderiza o estado conforme `## UX`; tokens do `DESIGN.md`.

**Migração:** nenhuma. Sem `skiko.renderApi=SOFTWARE`: a janela é opaca e fica em OpenGL (spike S0-07).

**Verificação visual:** a renderização Compose não entra nos testes automáticos; fica como AC-6 manual na revisão (captura em desktop e janela mínima).

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |
| AC-6 | manual | `app/src/test/kotlin/sfv2/app/ConnectionMonitorTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Arquivo de teste novo `ConnectionMonitorTest.kt`: o único existente na `app` é `ModuleTest.kt`, que só confere o nome do módulo.
- O app não reaproveita o `DaemonClient` da `cli`: `app` só pode depender de `core` (build-logic); o ping é pequeno e fica no próprio módulo, atrás de uma port para o teste usar um fake.
- A lógica de conexão fica fora do Compose (`ConnectionMonitor`) para ser testada sem janela; AC-6 (janela opaca, sem texto de issue) é verificado à mão na revisão.
- O app inicia o daemon por `systemctl --user start sfv2.service` (serviço da S5-01), não por processo filho: o daemon deve sobreviver ao fechar o app.
- Sem botão de tentar de novo: o ping periódico (3 s) recupera sozinho (AC-3).
- Dúvida de UX sobre IBM Plex e tema Charcoal resolvida: `DESIGN.md` é a autoridade de visual, então o app o adota como está.
- `gradle/libs.versions.toml` não tem camada; o engenheiro da `app` o altera para a dependência nova, como manda o CLAUDE.md. Se o Compose não resolver offline, o desenvolvimento bloqueia e o dono decide.

## Open questions
- none

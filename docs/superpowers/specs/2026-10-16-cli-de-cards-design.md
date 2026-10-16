# CLI de cards — spec

**Issue:** #18 · **Branch:** `feat/18-cli-de-cards` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** o dono precisa operar o núcleo (cards e eventos) antes de existir app.
**Audience:** o desenvolvedor sozinho com muitos projetos (e o desenvolvedor dentro de uma empresa), como dono único da fábrica.
**Acceptance criteria**
- **AC-1** — Dado o daemon rodando, quando o dono usa `card create`, `list`, `move`, `show` e `events`, então cada comando reflete o estado do daemon.
- **AC-2** — Dado o daemon parado, quando qualquer comando roda, então a CLI diz que o daemon não está rodando e como iniciá-lo, com saída diferente de 0.
- **AC-3** — (e2e) Dado um card criado e movido por todas as colunas, quando o daemon é reiniciado, então o histórico está íntegro; e dois clientes conectados recebem o mesmo evento.
- **AC-4** — Dado o `owner.token` legível ao lado do socket, quando o dono roda `card create` ou `card move`, então a CLI o envia como `proof`, o daemon aceita e o evento registra o ator dono.
- **AC-5** — Dado o `owner.token` ausente ou ilegível, quando o dono roda `card create` ou `card move`, então a CLI falha com saída diferente de 0, com mensagem que diz como resolver, sem enviar o pedido ao daemon.
**Out of scope:** app desktop, servidor MCP, comandos de agente, edição ou exclusão de cards, formatação rica de saída, iniciar o daemon pela CLI.
**Product fit:** serve "a fábrica é dona do próprio estado" e "toda ação é um evento com ator" (ator dono com prova); CLI é cliente do daemon por socket local, sem contradizer posicionamento nem escopo excluído.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contrato:** nenhum novo. A CLI usa o protocolo JSON por linha existente do daemon: `card.create` (`actor`, `title`, `proof`), `card.move` (`actor`, `id`, `to`, `proof`), `card.list`, `card.show` (`id`), `events.subscribe` (`after` opcional). Ator sempre `owner`.

**Comandos (Clikt, `cli`):**
- `sfv2 card create <título>` → imprime `<id>  <COLUNA>  <título>`.
- `sfv2 card list` → uma linha por card, no mesmo formato.
- `sfv2 card move <id> <coluna>` → imprime o card movido; coluna aceita o nome do enum sem distinção de caixa, com `-` ou `_` (`ready-for-dev`); coluna desconhecida → erro de uso (saída 2) listando as válidas.
- `sfv2 card show <id>` → uma linha do card.
- `sfv2 events [--after <seq>] [--count <n>]` → imprime cada linha de evento recebida, como JSON (uma por linha); sem `--after`, só eventos novos; com `--count`, sai com 0 após `n` eventos; sem `--count`, roda até o stream fechar (linha `{"error":...}` ou EOF do daemon → mensagem no stderr e saída 1).
- Erro do daemon (`ok:false`) → mensagem no stderr, saída 1. Daemon parado (socket ausente ou conexão recusada) → stderr: "daemon não está rodando; inicie com `./gradlew :daemon:run`", saída 1.
- Token: para `card create` e `card move`, lê `owner.token` ao lado do socket; ausente/ilegível/vazio → stderr com o caminho e "inicie o daemon (ele gera o arquivo)", saída 1, sem abrir conexão. `list`, `show` e `events` não precisam de token.

**Camada `cli` (arquivos):**
- `cli/src/main/kotlin/sfv2/cli/Main.kt`: `runCli` ganha o parâmetro `env: Map<String, String>` (padrão `System.getenv()` em `main`) e registra o grupo `card` e `events`.
- Novos em `cli/src/main/kotlin/sfv2/cli/`: `CardCommands.kt` (subcomandos Clikt), `DaemonClient.kt` (socket Unix, uma requisição por conexão; stream para `events`; devolve resultado tipado: `Ok`, `DaemonError(message)`, `Unavailable`), `SocketLocation.kt` (resolve `XDG_RUNTIME_DIR/sfv2/daemon.sock` e `owner.token` ao lado, mesma convenção do daemon), `OwnerToken.kt` (leitura do token).
- `cli/build.gradle.kts`: `implementation(libs.kotlinx.serialization.json)` (já no catálogo) e plugin de serialização só se necessário; `core` já é dependência (usa `Column`).
- `ARCHITECTURE.md` não muda: `cli` segue dependendo só de `core`, falando por socket.

**Camada `e2e`:** novo teste caixa-preta que sobe `launchDaemon` (daemon) com `XDG_RUNTIME_DIR`/`XDG_DATA_HOME` temporários, roda `runCli` contra ele, reinicia o daemon sobre o mesmo banco e confere o histórico; `e2e/build.gradle.kts` não muda.

`core`, `store` e `daemon` não mudam.

**Migração:** nenhuma.

## Test plan
_Tech Lead_

Testes de `cli` em processo contra um servidor de socket Unix falso (fake do protocolo) em diretório temporário, sem sleeps; o e2e usa o daemon real em processo.

| AC | Layer | Test file |
|---|---|---|
| AC-1 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-2 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-3 | e2e | `e2e/src/test/kotlin/sfv2/e2e/CardsCliTest.kt` |
| AC-4 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-5 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca 2 camadas (`cli` e `e2e`), pela regra de peso; o issue previa `standard`, mas a camada `e2e` conta mesmo sendo só de teste.
- A CLI reimplementa a resolução do socket e o cliente JSON em vez de depender do `daemon`: `ARCHITECTURE.md` limita `cli` a `core`, e o protocolo é o contrato.
- `events` imprime o JSON do evento sem reformatar: é pipeável e evita um segundo formato a manter; `--count` existe para a saída ser testável e usável em scripts.
- Um novo arquivo de teste `CardsCliTest.kt` em `e2e`: o único teste existente lá (`ClasspathTest`) só checa o classpath, e nenhuma suíte cobre o critério de saída da fase.
- Testes de `cli` no `MainTest.kt` existente com fake do socket (fake do protocolo, não mock de biblioteca).
- Saída 1 para erro do daemon, daemon parado e token ausente; 2 só para uso incorreto (convenção já em `runCli`).
- `list`, `show` e `events` sem token: o daemon não exige prova para leitura.
- Sem payload nem texto de card em logs; a CLI não loga, só escreve a resposta ao dono.

## Open questions
- none

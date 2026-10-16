# Visão do card — plano (#66)

Spec: `docs/superpowers/specs/2026-10-16-visao-do-card-design.md`. Peso `heavy`: duas camadas, daemon primeiro (o app depende do contrato `card.detail`).

## Tarefas

### T1 — daemon: comando `card.detail` (owner: engineer, layer `daemon`)
Sem dependências.
- `daemon/src/main/kotlin/sfv2/daemon/CardDetail.kt` (novo): função pura que monta o detalhe a partir de `Card`, `SpecRead` e `List<Event>`: spec cortada em 64 KiB com aviso `spec cortada`, 500 eventos mais recentes com `truncated`, payload cortado em 512 caracteres, runs derivados de `RUN_COST_RECORDED`, total `KNOWN|PARTIAL|UNKNOWN` (nunca soma zero em silêncio).
- `Protocol.kt`: `Request.CardDetail`, `Response.CardDetailResult`, parse e render.
- `SocketServer.kt`: roteia como leitura (sem prova do dono).
- `CardCommands.kt` e `Module.kt`: recebem o `SpecStore` e atendem o pedido.
- Testes: `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` (AC-1 a AC-6).

### T2 — app: modelo, cliente e tela (owner: engineer, layer `app`)
Depende de T1 (contrato).
- `app/build.gradle.kts`: `libs.kotlinx.serialization.json`.
- `DaemonClient.kt` (novo): `CardDetailSource` e `SocketCardDetailSource`.
- `CardDetailModel.kt` (novo): estado `Loading | Loaded | NotFound | Disconnected`; refaz o detalhe ao chegar evento do card (AC-8); formatação pura de custo e instante.
- `Theme.kt` (novo, move `Tokens`/`Texts` de `ConnectionScreen.kt`), `ConnectionScreen.kt`, `CardDetailScreen.kt` (novo).
- Testes: `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` (AC-7 a AC-10).

## Ordem
T1 → T2. Gates após cada tarefa: scope, acceptance-intact, migration, build.

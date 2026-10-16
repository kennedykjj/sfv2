# Faixa "A fábrica diz" no alto do quadro — plano (#235)

Issue #235. Spec: `docs/superpowers/specs/2026-10-16-faixa-a-fabrica-diz-no-alto-do-quadro-design.md`.

## Passos

1. **Daemon, protocolo (AC-2):** em `Protocol.kt`, `Request.FactoryPulse` para `factory.pulse`; teste em `ProtocolTest.kt`.
2. **Daemon, resposta (AC-1, AC-6):** `FactoryPulse.kt` monta estado (`AssistantStates.derive`), frase (`AssistantPhrases.say`) com rótulos `#n`/8 hex, capacidade e ids ativos do `RunPool`; `SocketServer.kt` roteia; teste em `SocketServerTest.kt` com título hostil e checagem de que nenhum evento foi gravado.
3. **App, fonte e modelo (AC-3, AC-4):** `PulseSource.kt` (socket) e `PulseModel.kt` (puro): estado em palavras, quadrados, medidor `est.`, "sem teto", última leitura esmaecida; testes em `BoardStateTest.kt`.
4. **App, tela (AC-5):** `PulseStrip.kt`, textos em `Theme.kt`, encaixe em `BoardScreen.kt` e leitura periódica em `Main.kt`.
5. **Verificação:** `./gradlew :daemon:test :app:test` e `./gradlew build`; capturas em desktop (≥1100 px) e estreito (<880 px).

Ordem: 1 → 2 (daemon verde) → 3 → 4 → 5. A previsão de fila vazia fica para outro card.

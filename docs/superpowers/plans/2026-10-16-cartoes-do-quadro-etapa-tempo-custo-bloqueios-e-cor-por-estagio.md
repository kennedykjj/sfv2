# Cartões do quadro: etapa, tempo, custo, bloqueios e cor por estágio — plano

Spec: `docs/superpowers/specs/2026-10-16-cartoes-do-quadro-etapa-tempo-custo-bloqueios-e-cor-por-estagio-design.md`

1. **Daemon (AC-1):** teste em `SocketServerTest.kt` para `card.list` com `enteredAt`, `cost` e `blocked`; depois `CardView`, `cardJson` e `viewOf` até ficar verde.
2. **App, leitura (AC-2):** teste em `BoardStateTest.kt` com resposta sem os campos novos; `BoardCard` com padrões e `parseCard` tolerante.
3. **App, modelo (AC-3, AC-4, AC-5):** testes em `BoardStateTest.kt` para os rótulos de etapa, tempo e custo com `Clock` fixo, para a marca de bloqueio e para o mapa coluna para matiz; implementar em `BoardState.kt`.
4. **App, tela (AC-6):** token `develop` e textos em `Theme.kt`; `BoardScreen.kt` desenha borda superior, número, meta e bloqueio; captura em desktop e estreito.
5. **Fechar:** `./gradlew :daemon:test :app:test` e `./gradlew build`.

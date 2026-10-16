# Aprovar e pedir mudança pela CLI — plano

**Issue:** #42 · **Spec:** `docs/superpowers/specs/2026-10-16-aprovar-e-pedir-mudanca-pela-cli-design.md`

## Tarefas

1. **daemon** (agente: engineer) — sem dependências.
   - `daemon/src/main/kotlin/sfv2/daemon/Protocol.kt`: `card.approve` e `card.request-change` viram `Request.CardApprove` e `Request.CardRequestChange` (toString mascara `proof`).
   - `daemon/src/main/kotlin/sfv2/daemon/CardCommands.kt`: `approve` e `requestChange` na região sincronizada de `move`. Exigem prova do dono, card existente e coluna SPEC_REVIEW; transição via `MoveRules.check`. Aprovar grava `OWNER_APPROVED` (`from`, `to`); pedir mudança grava `CARD_MOVED` (`from`, `to`, `note`). Nota vazia ou acima de 2048 bytes é recusada antes de gravar. Log só com `cmd` e id do card.
   - `daemon/src/main/kotlin/sfv2/daemon/SocketServer.kt`: roteamento dos dois requests novos.
2. **cli** (agente: engineer) — depende da tarefa 1.
   - `cli/src/main/kotlin/sfv2/cli/CardCommands.kt`: `card approve <id>` e `card request-change <id> --note <texto>`, com `ownerToken()` e `cli.call`, imprimindo `cardLine`.
3. **e2e** (agente: engineer) — depende das tarefas 1 e 2.
   - `e2e/build.gradle.kts`: `testImplementation` de `:store`, `:ports` e `:adapters`.
   - O teste de saída da fase, `PhaseExitTest`, é escrito pelo QA. O engineer só ajusta o build se faltar algo.

## Testes de aceitação (QA)

- AC-1 a AC-4: `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt`
- AC-5: `cli/src/test/kotlin/sfv2/cli/MainTest.kt`
- AC-6: `e2e/src/test/kotlin/sfv2/e2e/PhaseExitTest.kt`

## Notas

- `core`, `store`, `ports` e `adapters` não mudam.
- Sem migração.

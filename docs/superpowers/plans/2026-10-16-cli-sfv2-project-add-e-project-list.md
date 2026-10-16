# Plano: sfv2 project add e project list (#206)

Camada única: `cli`. Agente: engineer (execução solo pelo Tech Lead, peso standard).

## Tarefas

1. **cli** — criar `cli/src/main/kotlin/sfv2/cli/ProjectCommands.kt` com o grupo `project` (`add <caminho>`, `list`) e `withProjectCommands`; encadear em `Main.kt`.
   - `add`: caminho canônico (`toRealPath()`), `project.list` antes; se já registrado, imprime a linha e sai 0; senão `project.add` com o token do dono.
   - `list`: `project.list`, imprime `<name>  <repo>`, nunca `layers`.
   - Dependências: nenhuma. Testes de aceitação (QA) em `cli/src/test/kotlin/sfv2/cli/MainTest.kt`.

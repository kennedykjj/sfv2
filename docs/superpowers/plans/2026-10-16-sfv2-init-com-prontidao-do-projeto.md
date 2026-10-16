# sfv2 init com prontidão do projeto — plano

Spec: `docs/superpowers/specs/2026-10-16-sfv2-init-com-prontidao-do-projeto-design.md` (#82).

1. **Testes primeiro (red)** em `cli/src/test/kotlin/sfv2/cli/MainTest.kt`: um teste por AC-1 a AC-6, com o daemon falso existente e um `runCommand` falso que responde aos `git rev-parse` e `git show main:...`.
2. **Extrair o registro** de `ProjectAdd` em `ProjectCommands.kt` para uma função interna (consulta `project.list`, depois `project.add` com a prova do dono), reaproveitada pelo `init`.
3. **Criar `InitCommand.kt`:** validar git (AC-1), `factory.yml` na `main` (AC-2), registrar (AC-3, AC-5, AC-6), calcular e imprimir o score dos três documentos (AC-3, AC-4). Saída 1 em bloqueio.
4. **Ligar em `Main.kt`:** `.withInitCommand(cli)`.
5. **Verificar:** `./gradlew :cli:test` e `./gradlew build`.

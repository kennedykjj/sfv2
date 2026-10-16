# Registro de projetos — plano (#20)

Peso `standard`, uma camada (`daemon`), sem agente próprio: o Tech Lead implementa sozinho.

## Tarefas

1. **daemon — dependência YAML.** `gradle/libs.versions.toml` (snakeyaml-engine, versão fixa) e `daemon/build.gradle.kts`.
2. **daemon — registro.** `ProjectConfigSource.kt` (interface + `GitProjectConfigSource`), `ProjectRegistry.kt` (`register`, `list`, exceções, validação de `factory.yml`, reuso de `ProjectPolicies.from`).
3. **daemon — protocolo.** `Protocol.kt` (`project.add`, `project.list`, `ProjectResult`/`ProjectsResult`), `SocketServer.kt` (despacho com `OwnerProof` antes de ler arquivo), `Main.kt` (fiação).

Dependências: 1 → 2 → 3. Agente: Tech Lead (solo).

## Testes

`daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` (QA), cobrindo AC-1 a AC-7.

# Bedrock sem perfil preserva o AWS_PROFILE do daemon — plano (#223)

Spec: `docs/superpowers/specs/2026-10-16-bedrock-sem-perfil-preserva-aws-profile-design.md`

## Passos

1. QA (red): em `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt`, ajustar o teste `provider AC-4 a configured provider replaces stale provider variables...` para Bedrock sem perfil afirmar `AWS_PROFILE` igual ao do daemon (AC-1, AC-4) e adicionar testes para perfil configurado vencendo o do daemon (AC-2) e para Anthropic, Vertex e proxy sem `AWS_PROFILE` (AC-3). Usar `probeEnv`, que já isola o ambiente do daemon.
2. Engenheiro (green): em `adapters/src/main/kotlin/sfv2/adapters/RuntimeProvider.kt`, adicionar `fun inheritedVariables(): Set<String> = emptySet()` à interface e sobrescrever em `Bedrock` devolvendo `setOf("AWS_PROFILE")` quando `profile == null`.
3. Engenheiro: em `adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt`, trocar `removeAll(PROVIDER_VARIABLES)` por `removeAll(PROVIDER_VARIABLES - provider.inheritedVariables())`.
4. Rodar `./gradlew :adapters:test` e `./gradlew build`.

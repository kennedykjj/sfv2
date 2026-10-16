# CLI nativa com GraalVM — plano

Spec: `docs/superpowers/specs/2026-10-16-cli-nativa-com-graalvm-design.md`

1. Adicionar o plugin `org.graalvm.buildtools.native` em `gradle/libs.versions.toml`, com versão fixa.
2. Aplicar o plugin em `cli/build.gradle.kts`: imagem `sfv2`, `mainClass` `sfv2.cli.MainKt`, `--no-fallback`; `nativeCompile` fora do `build` e do `check`.
3. Criar `cli/src/main/resources/META-INF/native-image/sfv2/cli/resource-config.json` incluindo `sfv2/cli/build.properties`.
4. Reforçar em `cli/src/test/kotlin/sfv2/cli/MainTest.kt` que `--version` imprime a versão do build (AC-3).
5. Rodar `./gradlew build` e `./gradlew :cli:test`; com GraalVM, rodar `:cli:nativeCompile` e comparar `--version`, `doctor` e `card list` com a CLI JVM.

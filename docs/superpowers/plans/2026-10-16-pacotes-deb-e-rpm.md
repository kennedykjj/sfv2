# Pacotes deb e rpm — plano

1. Em `app/src/test/kotlin/sfv2/app/ModuleTest.kt`, escrever os testes de AC-1 a AC-3 (leem `app/build.gradle.kts` como texto) e vê-los falhar.
2. Em `app/build.gradle.kts`, dentro de `compose.desktop.application`, acrescentar `nativeDistributions` com `targetFormats(TargetFormat.Deb, TargetFormat.Rpm)`, `packageName = "sfv2"`, `packageVersion` no formato `MAJOR.MINOR.PATCH`, `description` e `vendor`.
3. Rodar `./gradlew :app:test` e `./gradlew build`; os testes ficam verdes.
4. Verificação manual (AC-4): `./gradlew :app:packageDeb :app:packageRpm` numa máquina com `rpm` e `fakeroot`; conferir os arquivos em `app/build/compose/binaries/main/`.
5. Passo do dono (AC-5), fora da fábrica: copiar `docs/context/rascunho-release-workflow.yml` para `.github/workflows/release.yml`.

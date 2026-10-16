# CLI nativa com GraalVM — spec

**Issue:** #84 · **Branch:** `feat/84-cli-nativa-com-graalvm` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** o `sfv2` hoje exige uma JVM instalada e paga a partida da JVM a cada chamada. A CLI é chamada o tempo todo (scripts, `doctor`, `card`, ganchos), então a partida lenta e a dependência do JDK pesam no uso diário.
**Audience:** o dono, ao operar a fábrica pelo terminal, e quem instala a fábrica numa máquina sem JDK.
**Acceptance criteria**
- **AC-1** — Given uma máquina com GraalVM (JDK com `native-image`), when o dono roda `./gradlew :cli:nativeCompile`, then é gerado o binário nativo `sfv2` em `cli/build/native/nativeCompile/`.
- **AC-2** — Given uma máquina sem GraalVM, when o dono roda `./gradlew build`, then o build e os testes passam sem exigir `native-image` (a compilação nativa é opt-in e não entra no `build`).
- **AC-3** — Given o binário nativo, when o dono roda `sfv2 --version`, then ele imprime a mesma versão do build JVM, lida do recurso `sfv2/cli/build.properties` embutido no binário.
- **AC-4** — Given o binário nativo e o daemon ativo, when o dono roda `sfv2 doctor` e `sfv2 card list`, then a saída e o código de saída são iguais aos da CLI JVM (socket Unix, token do dono e serialização JSON funcionam sem reflexão faltando).
- **AC-5** — Given o binário nativo, when o dono roda `sfv2 --version` com medição de tempo, then a partida leva menos de 100 ms, contra a partida da JVM.
**Out of scope:** macOS e Windows (decisão 20/21, #79/#80 adiadas; só Linux por ora); empacotamento e distribuição do binário; atualizador automático (#81); compilar a nativa no CI; nativa do daemon e do app.
**Product fit:** reduz o custo de usar a CLI e tira a exigência de JDK para quem só opera a fábrica, sem mudar o protocolo do socket.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada tocada: `cli`. Caminhos:
- `cli/build.gradle.kts`: aplica o plugin `org.graalvm.buildtools.native`, registra a imagem `sfv2` com `mainClass` `sfv2.cli.MainKt` e `--no-fallback`; `nativeCompile` fica fora do `build`/`check`. Usa o toolchain GraalVM só nessa tarefa.
- `gradle/libs.versions.toml`: entrada do plugin `graalvm-native` com versão fixa.
- `cli/src/main/resources/META-INF/native-image/sfv2/cli/resource-config.json`: inclui `sfv2/cli/build.properties` na imagem (sem isso, a versão não é lida no binário).

Pontos de risco, a resolver na implementação e não por reflexão ampla:
- Clikt 5 carrega JNA para ler o terminal; se o JNA não for compatível com a imagem, a CLI cai para o terminal padrão (o comportamento hoje já é tolerante). O `--enable-native-access` do JVM não se aplica à imagem.
- `kotlinx.serialization` não usa reflexão com plugin de compilador: não precisa de metadado extra.
- Socket Unix (`UnixDomainSocketAddress`) é suportado pela imagem nativa; validar em AC-4.
- Nenhuma mudança em `core`, no protocolo do socket ou nas ferramentas MCP.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | manual | `./gradlew :cli:nativeCompile` numa máquina com GraalVM; conferir o binário em `cli/build/native/nativeCompile/sfv2` |
| AC-2 | manual | `./gradlew build` numa máquina sem GraalVM termina verde |
| AC-3 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-4 | manual | com o daemon ativo, comparar `sfv2 doctor` e `sfv2 card list` do binário com os da CLI JVM |
| AC-5 | manual | `time cli/build/native/nativeCompile/sfv2 --version` abaixo de 100 ms |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Compilação nativa opt-in e fora do `build`: o CI e as máquinas sem GraalVM continuam verdes; só Linux por ora (decisão 20/21).
- A dependência #80 (CI para macOS e Windows, adiada) não bloqueia: a entrega é só Linux e não usa o CI de pacotes.
- Só AC-3 tem teste automatizado (`MainTest.kt` já cobre `--version`); AC-1, AC-2, AC-4 e AC-5 dependem do `native-image`, que o ambiente de testes não tem, então ficam `manual` com o comando de verificação.
- Metadado de reflexão mínimo e explícito (só o recurso de versão), em vez do agente de rastreio: menos superfície e falha clara se algo faltar.

## Open questions
- none

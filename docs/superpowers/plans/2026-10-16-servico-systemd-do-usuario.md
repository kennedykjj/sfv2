# Serviço systemd do usuário — plano (#58)

Spec: `docs/superpowers/specs/2026-10-16-servico-systemd-do-usuario-design.md`. Camada única: `daemon`. Agente: `engineer`.

## Tarefas

1. **daemon — `ServiceUnit`** (sem dependências)
   - `daemon/src/main/kotlin/sfv2/daemon/ServiceUnit.kt`: `render(launcher: Path): String` pura; recusa launcher relativo ou com espaço, quebra de linha, `%`, `$` ou aspas (AC-2, AC-6, AC-8, AC-9, AC-10).
2. **daemon — `ServiceInstaller`** (depende de 1)
   - `daemon/src/main/kotlin/sfv2/daemon/ServiceInstaller.kt`: `SystemctlRunner`, `SystemctlResult`, versão por processo, `ServiceOutcome`, `install()`/`uninstall()`, `service.env` 0600 nunca sobrescrito, aviso de linger, recusa fora de Linux (AC-1, AC-3, AC-4, AC-5, AC-7, AC-8, AC-10, AC-11).
3. **daemon — comando e launcher** (depende de 2)
   - `daemon/src/main/kotlin/sfv2/daemon/Main.kt`: `main(args)` desvia `service ...` para `runServiceCommand(args, env, out, err): Int`.
   - `daemon/build.gradle.kts`: `applicationDefaultJvmArgs` com `-Dsfv2.launcher`.

Testes de aceitação: `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` (QA).

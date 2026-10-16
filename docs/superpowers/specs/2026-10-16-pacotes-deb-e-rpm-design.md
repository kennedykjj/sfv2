# Pacotes deb e rpm — spec

**Issue:** #72 · **Branch:** `feat/72-pacotes-deb-e-rpm` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** o app desktop só roda por `./gradlew :app:run`. O dono precisa instalar a sfv2 no Linux como um programa normal, com o JRE embutido, sem ter Java nem o repositório na máquina.
**Audience:** o dono, em Fedora (rpm) e em Debian/Ubuntu (deb).
**Acceptance criteria**
- **AC-1** — Given o módulo `app`, when o build é configurado, then `compose.desktop.application.nativeDistributions` declara os formatos `Deb` e `Rpm`, com `packageName = "sfv2"`.
- **AC-2** — Given a configuração de empacotamento, when ela é lida, then a versão do pacote é numérica no formato `MAJOR.MINOR.PATCH` (exigência do jpackage) e a descrição e o fornecedor estão preenchidos.
- **AC-3** — Given a configuração de empacotamento, when ela é lida, then o pacote declara o mesmo `mainClass` do app (`sfv2.app.MainKt`), para o executável instalado abrir a mesma janela de `:app:run`.
- **AC-4** — Given uma máquina Linux com `rpm` e `fakeroot`, when `./gradlew :app:packageDeb :app:packageRpm` roda, then gera um `.deb` e um `.rpm` em `app/build/compose/binaries/main/`, cada um com o JRE embutido.
- **AC-5** — Given o rascunho `docs/context/rascunho-release-workflow.yml`, when o dono o copia para `.github/workflows/release.yml`, then a tag `v*` publica os dois pacotes no release (passo à mão: `.github/` é proibido para a fábrica).
**Out of scope:** o workflow em `.github/` (à mão pelo dono); instalação por usuário sem root (tar.gz ou AppImage); macOS e Windows (#80, adiado); assinatura de pacote; ícone definitivo e entrada de menu além do padrão do jpackage.
**Product fit:** fecha a Fase 6 do roadmap (S6-10): sem pacote, o dono não usa o app fora do repositório.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Só a camada `app`:
- `app/build.gradle.kts`: acrescentar `nativeDistributions` dentro de `compose.desktop.application`, com `targetFormats(TargetFormat.Deb, TargetFormat.Rpm)`, `packageName`, `packageVersion`, `description` e `vendor`.
- `app/src/test/kotlin/sfv2/app/ModuleTest.kt`: testes de configuração que leem `app/build.gradle.kts` como texto.

Sem dependência nova, sem mudança em `gradle/libs.versions.toml` (o plugin do Compose já está no catálogo, versão 1.12.1). O caminho do `build.gradle.kts` do `app` é resolvido a partir do diretório do projeto do teste, sem rede nem processo.

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |
| AC-4 | manual | `docs/context/rascunho-release-workflow.yml` |
| AC-5 | manual | `docs/context/rascunho-release-workflow.yml` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Pacotes deb e rpm instalam no sistema e pedem root; "instalação por usuário" fica fora do escopo e vira card próprio (tar.gz ou AppImage) se o dono quiser, porque o rascunho já aponta o conflito e o jpackage não gera deb/rpm por usuário.
- Testes de configuração leem o texto de `app/build.gradle.kts` em vez de rodar o jpackage: o empacotamento real precisa de `rpm` e `fakeroot` e demora; a geração dos pacotes (AC-4) fica verificada à mão e no CI do release.
- Reaproveitado `ModuleTest.kt`, a suíte existente do módulo `app`; nenhum arquivo de teste novo.
- O workflow de release não é escrito pela fábrica: `.github/` é proibido (CLAUDE.md); o rascunho em `docs/context/` já cobre o passo.
- O texto do issue #72 não está acessível ao agente de refine; os critérios acima partem do título, do roadmap e do rascunho do workflow.

## Open questions
- none

# Interface Runtime e fake — plano

**Issue:** #21 · **Spec:** `docs/superpowers/specs/2026-10-16-interface-runtime-e-fake-design.md`

## Tarefas

1. **ports** (agent: tech lead, solo — uma camada, peso standard)
   - `ports/src/main/kotlin/sfv2/ports/Runtime.kt`: `Runtime`, `SessionRequest`, `SessionResult` (`Exited`, `Hung`).
   - `ports/src/main/kotlin/sfv2/ports/FakeRuntime.kt`: `FakeRuntime`, `SessionScript` (`Succeed`, `Fail`, `Hang`), roteiro esgotado lança exceção de domínio.
   - Dependências: nenhuma.

Testes de aceitação (QA): `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt`.

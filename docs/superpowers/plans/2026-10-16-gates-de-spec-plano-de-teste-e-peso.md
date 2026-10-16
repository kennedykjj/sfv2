# Gates de spec: plano de teste e peso — plano

**Issue:** #32 · **Spec:** `docs/superpowers/specs/2026-10-16-gates-de-spec-plano-de-teste-e-peso-design.md` · **Peso:** light

## Tarefas

### T1 — `daemon`: gate de spec puro (`SpecGates`)
- **Camada:** `daemon` · **Agente:** engineer (executado pelo Tech Lead, peso light, uma camada)
- **Arquivos:**
  - `daemon/src/main/kotlin/sfv2/daemon/SpecGates.kt` (novo): `SpecLayer`, `SpecWeight`, `SpecGate`, `SpecFinding`, `SpecCheck`, `SpecGates.check(specText, layers)`.
  - Teste (QA, já no commit de aceitação): `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt`.
- **Dependências:** nenhuma; a implementação só começa depois do commit de testes de aceitação (QA).
- **Comportamento (da seção Architecture):**
  - Plano de teste: lê `AC-n` em qualquer dos formatos (`**AC-n** —`, `- AC-n:`, `AC-n —`); cada AC precisa de linha em `## Test plan` com camada em `layers` ou `manual`, e para camada real, caminho entre crases casando com um glob de `tests`. Spec sem AC reconhecível → `TEST_PLAN`.
  - Peso calculado: `HEAVY` com 2+ camadas em `## Architecture` (pelo prefixo `path`), `## Security` diferente de `n/a`, `## Rollback` diferente de `n/a`, ou mais de 8 caminhos; `LIGHT` com 1 camada, até 3 caminhos, `Touches UI: no` e sem sinais acima; `STANDARD` no resto. Contagem de ACs não entra.
  - Peso declarado (`**Weight:**`): ausente, inválido ou menor que o calculado → `WEIGHT` com os dois valores. Declarado acima do calculado é aceito.
- **Restrições:** função pura, sem IO nem relógio; sem `!!`; enums no lugar de booleanos; nada de log de texto de spec.

## Ordem

1. QA: testes de aceitação em `SpecGatesTest.kt` (commit `test(...)` com `acceptance tests for #32`).
2. T1: implementar `SpecGates.kt` até os testes passarem.
3. Gates de escopo, forbidden, migração, build, green.

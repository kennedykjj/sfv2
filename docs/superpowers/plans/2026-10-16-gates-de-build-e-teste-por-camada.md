# Gates de build e teste por camada — plano (#31)

Spec: `docs/superpowers/specs/2026-10-16-gates-de-build-e-teste-por-camada-design.md`

## Tarefas

1. **core** — owner: `engineer` — sem dependências.
   - Arquivo: `core/src/main/kotlin/sfv2/core/AcceptanceRules.kt` (novo).
   - `AcceptancePatterns`, `AcceptanceVerdict`, `AcceptanceReason`, `AcceptanceRules.red/green/assertionEdit`.
   - Padrões padrão portados do `run.py` da V0; linha de saída truncada em 8 KiB antes de casar; regex inválido do chamador vira erro que nomeia o campo (AC-5).
   - Validação de seletor, `qaCommit` e arquivos (AC-4) como funções puras no `core`, usadas pelo daemon.
2. **daemon** — owner: `engineer` — depende da tarefa 1.
   - `GateExecutor.kt`: `GateSpec.check: GateCheck = ExitCode`, `GateCheck.Red/Green`, leitura da saída em streaming, `reason` no payload de `GATE_FINISHED`; timeout e `start_error` têm precedência.
   - `AcceptanceEditGate.kt` (novo): `git diff <qaCommit> HEAD -- <files>` pelo `GateRunner`, sem shell, validando entradas (AC-4), registrando `gate=acceptance-edit` pelo `GateExecutor`.
   - Teste do `AcceptanceEditGate` com repositório git temporário na suíte do daemon.

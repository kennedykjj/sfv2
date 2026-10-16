# Gates de build e teste por camada — spec

**Issue:** #31 · **Branch:** `feat/31-gates-de-build-e-teste-por-camada` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Um teste de aceitação só prova algo se falhou pelo motivo certo antes da implementação e se realmente rodou depois dela. Sem gates que verifiquem isso, o agente pode entregar teste que nem compila, teste que não executou ou teste enfraquecido para passar, e o card avança com base no relato do agente.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, na empresa, quem precisa de trilha de auditoria): confia no veredito do gate, não no relato do agente.
**Acceptance criteria**
- **AC-1** — Given testes de aceitação novos antes da implementação, when o gate vermelho roda a camada, then ele passa só se o teste falhou por asserção; passar, falhar por compilação/setup ou falhar sem linha de asserção na saída é fail, com o motivo.
- **AC-2** — Given a implementação, when o gate verde roda a camada, then ele passa só se o teste passou e a saída casa com o padrão `ran` da camada; teste que falha, ou que passa sem casar `ran` (nada rodou), é fail, com o motivo.
- **AC-3** — Given uma edição em arquivo de teste de aceitação depois do commit do QA, when o gate de edição confere o diff, then ele falha se alguma linha adicionada ou removida (fora comentário) casa com o padrão de asserção, e passa se a edição não toca asserção.
**Out of scope:** Aprovação do dono para edição de teste (`shipfloor accept`, G14), stub temporário do QA (`shipfloor:qa-stub`), o agendamento dos gates no pipeline (#27) e a leitura dos padrões do `factory.yml` por camada.
**Product fit:** Serve a "gates são scripts, não a palavra do agente". Não contradiz o posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core` (puro, sem IO): novo `AcceptanceRules.kt` em `core/src/main/kotlin/sfv2/core/` com
  - `AcceptancePatterns(ran: Regex, assertions: Regex? = null, setupErrors: Regex? = null)`; os padrões extras da camada somam-se aos padrões padrão (portados do `run.py` da V0: `SETUP_ERRORS`, `ASSERTIONS`; ERE vira regex Java, `[[:space:]]` vira `\s`).
  - `AcceptanceRules.red(exitCode: Int?, output: Sequence<String>, patterns): AcceptanceVerdict` e `AcceptanceRules.green(...)`.
  - `AcceptanceRules.assertionEdit(diff: Sequence<String>, keep: Regex): List<String>` devolve as linhas de asserção tocadas (vazio = ok); ignora cabeçalhos do diff e linhas só de comentário.
  - `AcceptanceVerdict` sealed: `Ok` | `Rejected(reason: AcceptanceReason)`; `AcceptanceReason` enum: `TESTS_PASSED_BEFORE_IMPL`, `SETUP_ERROR`, `NO_ASSERTION_FAILURE`, `TESTS_FAILED`, `NO_TEST_RAN`, `ASSERTION_EDITED`.
- `daemon`: `GateSpec` ganha `check: GateCheck = GateCheck.ExitCode` (sealed: `ExitCode`, `Red(patterns)`, `Green(patterns)`). `GateExecutor.execute` continua com a mesma assinatura; depois de rodar, o veredito vem de `check`: `ExitCode` mantém o comportamento atual; `Red`/`Green` leem o arquivo de saída em streaming (`Files.lines`, nunca inteiro em memória) e aplicam `AcceptanceRules`. O payload de `GATE_FINISHED` ganha `reason` com o valor do `AcceptanceReason` em minúsculas (`tests_passed_before_impl`...) além dos atuais; timeout e `start_error` seguem como antes e têm precedência.
- `daemon`: novo `AcceptanceEditGate(store, events, clock, runner)` com `check(cardId, worktree: Path, qaCommit: String, files: List<String>, keep: Regex, runDir: Path): GateVerdict`: roda `git -C <worktree> diff <qaCommit> HEAD -- <files>` pelo `GateRunner` (sem shell), lê a saída e aplica `assertionEdit`; registra `GATE_FINISHED` com `gate=acceptance-edit` pelo mesmo caminho do executor.

**Camadas e arquivos**
- `core`: `core/src/main/kotlin/sfv2/core/AcceptanceRules.kt` (novo).
- `daemon`: `GateExecutor.kt` (alterado), `AcceptanceEditGate.kt` (novo), em `daemon/src/main/kotlin/sfv2/daemon/`.
- Testes: `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt`, `core/src/test/kotlin/sfv2/core/AcceptanceRulesTest.kt`.

**Fluxo:** o chamador (dispatcher, #27) monta o `GateSpec` com o comando de teste da camada (`test` do config, seletor já substituído, sem shell) e o `check` do estágio. A saída completa fica em `gate-<name>.log` no run; o evento guarda só `reason`, resumo e caminho, nunca a saída inteira. Log `key=value` com id do card, gate e motivo.

**Migração:** nenhuma; `GATE_FINISHED` e `reason` são texto no payload (expand-only).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/AcceptanceRulesTest.kt` |

## Security
n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca duas camadas (`core` para as regras puras, `daemon` para a execução), como a nota da issue previa.
- Regras de classificação em `core` como funções puras sobre `Sequence<String>`: sem IO e testáveis sem processo (CLAUDE.md: funções puras no core).
- Red e green viram `GateCheck` no `GateSpec` em vez de novo executor: reaproveita timeout, arquivo de saída, evento e resumo do #26.
- Os padrões padrão de setup/asserção/`ran` vêm da V0 (`run.py`); os da camada chegam por parâmetro. Ler o `factory.yml` fica para o dispatcher (#27).
- Red nunca tenta de novo (teste que deve falhar não pode ser instável); retry do green fica com o chamador, que decide se roda o gate de novo.
- Gate de edição compara o commit do QA com `HEAD` via `git diff` por processo, sem shell, e ignora linhas só de comentário (lição do G14: comentário "fail by assertion" não conta como asserção).
- `acceptance-edit` não consulta aprovação do dono nem stub do QA; isso é o ponto único de aprovação do #40, fora desta spec.
- Teste de AC-1/AC-2 no `GateExecutorTest.kt` com `GateRunner` falso escrevendo saída conhecida; AC-3 em arquivo novo `AcceptanceRulesTest.kt` no `core`, porque nenhuma suíte do `core` cobre regras de aceitação. O teste do `AcceptanceEditGate` com repositório git temporário cabe ao engenheiro, na suíte do daemon.

## Open questions
- none

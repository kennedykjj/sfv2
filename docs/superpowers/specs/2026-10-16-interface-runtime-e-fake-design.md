# Interface Runtime e fake — spec

**Issue:** #21 · **Branch:** `feat/21-interface-runtime-e-fake` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** O modelo que executa uma sessão de agente precisa ser trocável e testável sem gastar tokens; hoje não há fronteira (port) que isole o runtime.
**Audience:** O desenvolvedor sozinho com muitos projetos (e, por extensão, quem mantém a fábrica): troca de modelo sem mexer na fábrica e suíte de testes gratuita.
**Acceptance criteria**
- **AC-1** — Dado a interface Runtime, quando uma sessão é lançada com estágio, worktree, modelo e configuração MCP, então o resultado devolve o código de saída e o caminho da transcrição.
- **AC-2** — Dado o runtime falso roteirizado para sucesso, quando uma sessão é lançada, então devolve código de saída 0 e um caminho de transcrição.
- **AC-3** — Dado o runtime falso roteirizado para falha, quando uma sessão é lançada, então devolve código de saída diferente de zero e um caminho de transcrição.
- **AC-4** — Dado o runtime falso roteirizado para travar, quando uma sessão é lançada, então o resultado indica trava (sem término espontâneo) de forma observável, sem sleep nem relógio real.
- **AC-5** — Dado o runtime falso, quando uma sessão é lançada, então o teste consegue ler com quais estágio, worktree, modelo e configuração MCP ela foi lançada.
- **AC-6** — Dado a suíte de testes que usa o runtime falso, quando roda, então não faz chamada de rede nem consome tokens.
**Out of scope:** Runtime real (Claude Code ou outro), parsing de transcrição e custo, timeout/watchdog do daemon, pipelines e escolha de modelo por estágio.
**Product fit:** Serve "custo medido das transcrições" e "gates, não a palavra do agente": o runtime só devolve código de saída e transcrição, sem relato do agente; habilita o roadmap da fase 2 (S2-03). Não contradiz posicionamento nem escopo excluído.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada tocada: só `ports` (`ports/src/main/kotlin/sfv2/ports/`), sem dependência nova; sem migração.

Contratos novos (todos em `sfv2.ports`):
- `interface Runtime { fun launch(request: SessionRequest): SessionResult }`.
- `data class SessionRequest(stage: String, worktree: Path, model: String, mcpConfig: Path)`.
- `sealed interface SessionResult`: `Exited(exitCode: Int, transcriptPath: Path)` e `Hung(transcriptPath: Path)`.
- `class FakeRuntime(script: List<SessionScript>)`: consome um roteiro por chamada (`Succeed`, `Fail(exitCode)`, `Hang`), devolve o `SessionResult` correspondente com um caminho de transcrição determinístico, e expõe `launched: List<SessionRequest>`. Sem IO, sem relógio, sem rede; `Hang` devolve `Hung` na hora. Roteiro esgotado lança exceção de domínio (`RuntimeException`) com o índice da chamada.

Arquivos: `Runtime.kt`, `FakeRuntime.kt` (ambos em `ports/src/main/kotlin/sfv2/ports/`) e `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` (fixtures em `object RuntimeFixtures` no mesmo arquivo).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |
| AC-2 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |
| AC-3 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |
| AC-4 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |
| AC-5 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |
| AC-6 | ports | `ports/src/test/kotlin/sfv2/ports/RuntimeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Estágio como `String` em `SessionRequest`: o core não tem tipo de estágio e os pipelines (fase 2) ainda vão defini-lo; trocar por tipo depois é mudança pequena.
- Trava é um resultado (`Hung`), não exceção nem timeout: o watchdog é do daemon (fora de escopo) e o fake não pode dormir.
- Arquivo `RuntimeTest.kt` novo: o único teste de `ports` é o placeholder `ModuleTest.kt`, que não cobre Runtime.
- AC-6 é garantido por construção (o fake não tem IO); o teste confere que o fake roda sem tocar o disco (transcrição é só um `Path` devolvido).
- Peso `standard` (a issue esperava `light`): uma camada e 3 arquivos, mas 6 ACs passam do limite de 3 do `light`.

## Open questions
- none

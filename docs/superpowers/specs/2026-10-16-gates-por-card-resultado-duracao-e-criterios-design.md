# Gates por card: resultado, duração e critérios ligados aos testes — spec

**Issue:** #233 · **Branch:** `feat/233-gates-por-card-resultado-duracao-e-criterios` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Hoje o dono só vê um gate como um evento solto na linha do tempo do card. Não há uma visão que diga, por card, quais gates rodaram, se passaram, quanto cada um levou e se cada critério de aceite tem um teste que de fato rodou verde. Para saber por que um card está bloqueado, o dono lê eventos crus. A duração nem é gravada: `GATE_FINISHED` não carrega o tempo do gate.
**Audience:** o dono, ao decidir sobre um card parado ou ao revisar uma entrega.
**Acceptance criteria**
- **AC-1** — Given um gate que executa um processo, when o `GateExecutor` o termina, then o evento `GATE_FINISHED` carrega `duration_ms` com o tempo medido pelo `Clock` injetado, e um gate que estoura o tempo também grava a duração.
- **AC-2** — Given `card.detail` de um card com eventos `GATE_FINISHED`, when o dono pede o detalhe, then a resposta traz `gates` com o último resultado de cada gate (nome, veredito, motivo, `duration_ms`, instante), na ordem em que cada gate apareceu pela primeira vez, e um gate sem duração gravada (escopo, spec, eventos antigos) tem `duration_ms` ausente, nunca zero.
- **AC-3** — Given um card cuja spec registrada tem tabela de Test plan, when o dono pede o detalhe, then a resposta traz `criteria` com uma linha por AC: `ac`, `layer`, `test_file` e `gate` (o veredito mais recente do gate `test-<layer>`, ou `none` quando esse gate ainda não rodou, ou `manual` quando a camada é `manual`).
- **AC-4** — Given um card sem spec registrada, sem eventos de gate ou com spec divergente, when o dono pede o detalhe, then `gates` e `criteria` chegam como listas vazias e o restante da resposta não muda.
- **AC-5** — Given uma resposta de `card.detail` sem `gates` e `criteria` (daemon antigo), when o app a lê, then o card carrega normalmente com as duas listas vazias; e dada uma resposta com os campos, o app expõe cada gate e cada critério com os mesmos valores.
- **AC-6** — Given o card aberto no app, when o dono olha a seção Gates, then vê cada gate com veredito (ok/bad), duração legível e motivo, e cada critério com o arquivo de teste e o estado do gate da camada; gate sem duração mostra um traço.
**Out of scope:** reexecutar gate pela interface; abrir o log completo do gate (`output_file`); histórico de todas as execuções do mesmo gate (só a última); mudar o que cada gate verifica; ligar o critério ao teste individual (o vínculo é por camada, via o gate `test-<layer>`).
**Product fit:** responde ao gargalo G9 (saber por que um gate falhou antes de subir de modelo) e à aba Gates do card view descrita em `DESIGN.md`.
**Touches UI:** yes

## UX
_UX/UI Designer_

Seção Gates dentro do card view existente (`CardDetailScreen`), seguindo `DESIGN.md`: veredito com `colors.ok` / `colors.bad` (nunca o accent), texto meta de 13px para nome, duração e motivo. Duração: `850 ms`, `12 s`, `3 min 20 s`; sem valor, `—`. Critérios abaixo dos gates: `AC-n`, arquivo de teste e o estado do gate da camada (`ok`, `bad`, `não rodou`, `manual`). Sem gates e sem critérios: uma linha `faint` ("Nenhum gate rodou ainda"), sem esqueleto.

## Architecture
_Tech Lead_

Mudança aditiva no contrato `card.detail`: os campos novos `gates` e `criteria` não alteram os existentes.

- `daemon/src/main/kotlin/sfv2/daemon/GateExecutor.kt` — mede o tempo com o `Clock` injetado (instante antes e depois de `runner.run`) e grava `duration_ms` no payload, inclusive no timeout e no `start_error`.
- `daemon/src/main/kotlin/sfv2/daemon/SpecGates.kt` — expõe `plan(specText): List<PlanRow>` (AC, camada, arquivo), reaproveitando o parser de tabela que `check` já usa, sem duplicar regex.
- `daemon/src/main/kotlin/sfv2/daemon/CardDetail.kt` — monta `gates` (último `GATE_FINISHED` por nome) e `criteria` (plano da spec cruzado com o gate `test-<layer>`); continua pura, sem IO.
- `app/src/main/kotlin/sfv2/app/CardDetailModel.kt` — `GateRow` e `CriterionRow` em `CardDetail`; os campos ausentes viram lista vazia.
- `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt` — seção Gates com o formato de duração.

O app continua lendo só pelo socket do daemon; nenhum cliente toca o banco.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/GateExecutorTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-6 | manual | n/a |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Duração gravada no evento, não derivada de eventos vizinhos: o `GATE_FINISHED` é o único evento do gate e não há evento de início; medir no executor é exato e não muda o modelo de eventos.
- Gates gravados sem processo (`scope`, `test-plan`, `weight`) ficam sem `duration_ms`: não houve execução a medir, e zero enganaria o dono.
- Critério liga ao teste pela camada (gate `test-<layer>`), não pelo método: o gate roda a tarefa da camada inteira; ligar ao método exigiria parsear a saída do Gradle, fora do escopo.
- Só o último resultado por gate aparece: o histórico completo já está na linha do tempo de eventos.
- `card.detail` ganha campos, não ganha comando novo: mantém o contrato anterior e reaproveita a assinatura do feed que já refaz a leitura a cada evento.
- Teste de duração precisa de um `Clock` que avança a cada leitura, definido no arquivo de teste existente; sem `Thread.sleep` nem relógio real.
- Sem arquivo de teste novo: `GateExecutorTest`, `SocketServerTest` (onde vivem os testes de `card.detail`) e `CardDetailModelTest` já cobrem os módulos.
- AC-6 é `manual`: a seção é Compose sem suíte de teste de tela no `app`; verificar com captura em desktop e mobile conforme `CLAUDE.md` do dono.

## Open questions
- none

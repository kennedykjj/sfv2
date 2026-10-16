# Outbox com confirmação por padrão — spec

**Issue:** #88 · **Branch:** `feat/88-outbox-com-confirmacao-por-padrao` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problem:** Hoje um marco opcional alcançado é escrito no tracker externo na hora, sem o dono saber. Na empresa isso quebra a regra "nada sai da máquina sem evento assinado pelo dono ou política explícita que dispense".
**Audience:** O desenvolvedor dentro de uma empresa (Jira, trilha de auditoria): ele decide e assina o que sai da máquina.
**Acceptance criteria**
- **AC-1** — Dado `signers.milestone` = `owner` (padrão), quando um card alcança um marco opcional, então nada é escrito no tracker e fica registrado um MILESTONE_PENDING, uma única vez por marco por card.
- **AC-2** — Dado `signers.milestone` = `policy`, quando um card alcança um marco opcional, então ele é escrito no tracker na hora (MILESTONE_WRITTEN), como hoje, sem pendência.
- **AC-3** — Dado marcos pendentes, quando o dono pede a lista pelo socket, então recebe cada pendência com card, marco e texto a ser escrito.
- **AC-4** — Dado um marco pendente, quando o dono o confirma pelo socket com a prova válida, então é registrado MILESTONE_CONFIRMED (ator dono, ligado ao hash do texto pendente) e só depois o marco é escrito no tracker (MILESTONE_WRITTEN).
- **AC-5** — Dado um marco pendente, quando a confirmação chega com prova ausente ou errada, ou de ator que não é o dono, então é recusada e nada é escrito nem registrado como confirmado.
- **AC-6** — Dado um marco já confirmado, quando o dono confirma de novo, então nada é escrito outra vez no tracker.
**Out of scope:** Tela no app ou comando de CLI para listar/confirmar (só socket); fechamento (CLOSE), que já exige confirmação do dono e não muda; importação (é leitura); rejeitar ou editar uma pendência.
**Product fit:** Serve ao princípio "toda ação é um evento com ator; a aprovação do dono é um evento assinado" e à restrição "nada sai da máquina sem evento assinado pelo dono ou política explícita". Não contradiz o posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos (core):** `EventType` ganha `MILESTONE_PENDING` e `MILESTONE_CONFIRMED`. Payload do pendente: `milestone`, `externalId`, `textHash` (sha-256 do texto a escrever; o texto em si não vai para o evento). Confirmado: `milestone`, `textHash`, ator `Actor.Owner`. Sem outra mudança em `core`: `Signer` e `signers` já existem em `ProjectPolicies`.
- **Contratos (daemon):** `Request.MilestoneList` (somente leitura) e `Request.MilestoneConfirm(actor, id, milestone, textHash, proof)` com `toString` que mascara a prova, como `CardCloseConfirm`; `Response.MilestonesResult(items)` com `card`, `milestone`, `text`, `textHash`. Comandos de socket `milestone.list` e `milestone.confirm`, parseados em `Protocol.kt`; o `when` de `CardCommands.kt` e o de `SocketServer.kt` ganham os novos casos.
- **Comportamento:** `ExternalMilestones.reached` consulta `policies.signers[MILESTONE]`. `policy` → caminho atual. `owner` → calcula o texto, registra `MILESTONE_PENDING` uma vez por marco por card (ator adaptador) e não toca o tracker. `ExternalMilestones.handle` atende a lista (pendências = `MILESTONE_PENDING` sem `MILESTONE_WRITTEN` do mesmo marco, derivadas dos eventos de `store.cards()`) e a confirmação: exige `Actor.Owner` e `ownerProof.matches`, hash igual ao do pendente, grava `MILESTONE_CONFIRMED`, escreve no tracker e grava `MILESTONE_WRITTEN`; segunda confirmação é no-op; falha do tracker não grava `MILESTONE_WRITTEN` (a confirmação pode ser repetida). Serializado, como hoje. Só ids em log.
- **Ligação:** o mesmo `ExternalMilestones` precisa ser visto por `RefineRuntime.kt` (dispatcher) e pelo `SocketServer`/`Main.kt`; recebe o `OwnerProof` por parâmetro.
- **Camadas e arquivos:** `core` — `core/src/main/kotlin/sfv2/core/Event.kt`; `daemon` — `daemon/src/main/kotlin/sfv2/daemon/ExternalMilestones.kt`, `Protocol.kt`, `SocketServer.kt`, `CardCommands.kt`, `Main.kt`, `RefineRuntime.kt` e o teste `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` (existente: os testes atuais passam a declarar `signers.milestone: policy`).
- **Migração:** nenhuma (eventos novos, sem mudança de esquema). Projetos que listam `milestones` sem declarar `signers.milestone` passam a reter os marcos até a assinatura; para manter o comportamento antigo, declarar `policy`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ExternalMilestonesTest.kt` |

| AC | Layer | Test file |
|---|---|---|

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- PO: só socket nesta entrega (sem CLI/app), por orientação do Tech Lead, para manter o card pequeno.
- Padrão `owner` para o marco, como o título pede ("confirmação por padrão"); quem quer o comportamento antigo declara `signers.milestone: policy`.
- Peso `heavy`: toca duas camadas (`core` e `daemon`), apesar do card esperar `standard`.
- Sem arquivo de teste novo: `ExternalMilestonesTest.kt` já cobre o módulo.
- O texto do marco não vai no evento pendente, só o hash (texto externo não é logado); a lista o recompõe do estado do card.

## Open questions
- none

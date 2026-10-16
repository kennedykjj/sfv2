# Gate de DoR na importação — spec

**Issue:** #86 · **Branch:** `feat/86-gate-de-dor-na-importacao` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** um ticket sem o mínimo (título, corpo, critério de aceitação, label) entra na fábrica e gasta refinamento e custo de agente antes de alguém notar que não dava para começar. "Card sem DoR não começa."

**Audiência:** o desenvolvedor dentro de uma empresa (Jira, convenções da empresa).

**Critérios de aceitação**
- AC-1: Dado a política de DoR ligada no projeto, quando importo (por id, por busca ou pelo board V0) um ticket sem algum campo exigido, então ele entra como card em Inbox bloqueado (CARD_BLOCKED) e o motivo lista os nomes dos campos que faltam, sem texto do ticket.
- AC-2: Dado a política de DoR ligada, quando importo um ticket que cumpre todos os campos exigidos, então ele entra em Inbox sem bloqueio.
- AC-3: Dado a política de DoR desligada ou ausente (padrão), quando importo qualquer ticket, então nenhum card é bloqueado por DoR.
- AC-4: Dado um card bloqueado por DoR, quando o ticket é corrigido e reimportado, então o card continua bloqueado; só o desbloqueio do dono (já existente) o libera.

**Fora de escopo:** sincronização contínua com o tracker; reavaliar a DoR depois da importação; desbloqueio automático; validar a qualidade do conteúdo (só presença dos campos); UI nova para configurar a política.

**Aderência ao produto:** serve "gates são scripts, não a palavra do agente" (checagem determinística) e "o dono aparece só onde a decisão é dele" (desbloqueio é do dono). Não contradiz o posicionamento nem o fora de escopo: o tracker é tocado só na importação.

**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** `ProjectPolicies` ganha `dor: Set<DorField>` (vazio = política desligada, o padrão). `enum DorField(key)`: `TITLE("title")`, `BODY("body")`, `ACCEPTANCE_CRITERIA("acceptance_criteria")`, `LABEL("label")`. Config do projeto: chave `dor:` com lista de nomes de campo; valor desconhecido ou formato errado lança `InvalidPolicyException("dor...")`. Função pura `DorPolicy.missing(required, title, body, labels): List<DorField>` no `core` (título em branco, corpo em branco, corpo sem a palavra "critérios de aceitação"/"acceptance criteria" sem diferenciar caixa, nenhuma label), na ordem do enum.
- **Importação:** `CardImport.create` e o ramo "criar" do `upsert`, da busca e do board V0 chamam `missing` com `project.policies.dor`; se a lista não é vazia, logo após o `CARD_CREATED` grava `CARD_BLOCKED` (ator `Actor.Factory("dor-gate")`, payload `reason = "dor_missing: <chaves separadas por vírgula>"`). Só cria; `reimport` não reavalia nem desbloqueia (o desbloqueio do dono é o já existente em `hasOpenBlock`/`CostBudget`). Log `key=value` com id do card e campos, nunca texto do ticket.
- **Camadas e arquivos:** `core` (`ProjectPolicies.kt`, novo `DorPolicy.kt`); `daemon` (`CardImport.kt`, `ProjectRegistry.kt`: `POLICY_KEYS` ganha `"dor"`; `Protocol.kt` e `McpServerFactory.kt`: expor `dor` em `policies`/`policiesJson`).
- **Migração:** nenhuma; projeto sem `dor:` mantém o comportamento atual.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/CardImportTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Política `dor` como lista de campos exigidos no config do projeto: liga/desliga e escolhe os campos num só lugar, sem nova camada de config.
- O motivo do bloqueio traz só nomes de campo: texto de ticket é dado não confiável e não vai para evento nem log.
- Bloqueio por `CARD_BLOCKED` já existente: o dono desbloqueia pelo mecanismo atual, sem estado novo.
- "Critério de aceitação" é checado por presença da expressão no corpo: gate determinístico, sem julgar qualidade.
- Reimportação não reavalia a DoR (fora de escopo, mantém importar-e-fechar sem sincronização).
- Sem arquivo de teste novo: AC-1/2/4 cabem em `CardImportTest` e AC-3 (padrão desligado, parse da chave) em `ProjectPoliciesTest`.
- Peso `standard`: duas camadas, 4 ACs, 6 arquivos.

## Open questions
- none

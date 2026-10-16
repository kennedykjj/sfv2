# Porta única de texto externo — spec

**Issue:** #57 · **Branch:** `feat/57-porta-unica-de-texto-externo` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** texto vindo de fora (issue, ticket, comentário) é dado não confiável. Hoje a garantia de que ele entra por uma porta só, marcado como tal, e de que a fábrica não toca o tracker fora dos marcos permitidos não está provada de ponta a ponta.
**Audience:** o desenvolvedor dentro de uma empresa (Jira, trilha de auditoria), que precisa confiar que texto externo nunca vira instrução para um agente.
**Acceptance criteria**
- **AC-1** — Given um card com texto importado do tracker, When um agente o consulta, Then o texto só chega pelo MCP, envolvido e marcado como não confiável.
- **AC-2** — Given o conjunto de ferramentas oferecidas aos agentes, When elas são inspecionadas, Then nenhuma lê o tracker diretamente.
- **AC-3** — Given um tracker falso (teste e2e), When o card é importado, trabalhado e fechado com confirmação, Then não há nenhuma chamada ao tracker entre a importação e o fechamento.
**Out of scope:** UI; novos marcos ou sincronização com o tracker; guardar o corpo importado (só o hash segue guardado); mudar o formato do envoltório de texto não confiável.
**Product fit:** serve ao princípio "texto externo entra por uma porta só, marcado como não confiável" e a "importar e fechar, sem sincronização contínua". Não contradiz o posicionamento nem o fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** nenhum muda. As ferramentas MCP (`card_get`, `spec_get`, `review_criteria_get`, `policies_get`, `layers_get`, `completion_report`, `file_write_request`) e o envoltório `UntrustedText` (`source`, `trust`, `text`) ficam como estão.
- **Camadas e módulos:** `daemon` (`McpServerFactory`, `McpReadTools`) e `e2e`. Só testes; código de produção muda apenas se um teste revelar uma ferramenta que devolve texto de card fora do `UntrustedText`.
- **AC-1/AC-2:** testes em `McpServerFactoryTest` com o servidor MCP real e o card importado por `CardImport` com `FakeTracker`: todo campo de texto externo (título do card) sai como `{source, trust: "untrusted", text}`; `McpServerFactory` e `McpReadTools` não recebem `Tracker` (nem `Forge`) no construtor, e nenhuma ferramenta listada chama o tracker (`FakeTracker.calls` fica vazio depois de chamar cada ferramenta).
- **AC-3:** `PhaseFourExitTest` no e2e: `CardImport` + pipelines com runtime e forge falsos + `CardClosure` (rascunho e confirmação), tudo sobre o mesmo `FakeTracker`; sem marcos nas políticas. Afirma `calls` igual ao instante pós-importação até a confirmação, e `close` chamado uma vez só na confirmação.
- **Migração:** n/a.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt` |
| AC-3 | e2e | `e2e/src/test/kotlin/sfv2/e2e/PhaseFourExitTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Sem mudança de código de produção planejada: a porta única já existe (`UntrustedText` no MCP; corpo importado só como hash); a spec a prova com testes.
- `PhaseFourExitTest.kt` é arquivo novo: nenhuma suíte e2e cobre a fase 4 (as existentes são das fases 2 e 3).
- Peso `standard`: duas camadas, 3 ACs, sem segurança nem migração destrutiva.
- Sem seção Security: o card não tem label `cybersec`.

## Open questions
- none

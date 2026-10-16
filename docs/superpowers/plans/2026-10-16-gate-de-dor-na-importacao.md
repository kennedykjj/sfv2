# Gate de DoR na importação — plano

**Issue:** #86 · **Spec:** `docs/superpowers/specs/2026-10-16-gate-de-dor-na-importacao-design.md`

## Tarefas

### T1 — core (agente: engineer)
Sem dependências.
- `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt`: `enum class DorField(key)` (`TITLE`, `BODY`, `ACCEPTANCE_CRITERIA`, `LABEL`); `ProjectPolicies.dor: Set<DorField> = emptySet()`; `from` lê a chave `dor` (lista de nomes; valor desconhecido ou formato errado lança `InvalidPolicyException("dor...")`).
- `core/src/main/kotlin/sfv2/core/DorPolicy.kt` (novo): `DorPolicy.missing(required, title, body, labels): List<DorField>`, pura, na ordem do enum.

### T2 — daemon (agente: engineer)
Depende de T1.
- `daemon/.../CardImport.kt`: `create` e o ramo "criar" do `upsert`, da busca e do board V0 chamam `missing`, só quando o card nasce em `INBOX`; se faltar campo, grava `CARD_BLOCKED` logo após `CARD_CREATED` (ator `Actor.Factory("dor-gate")`, `reason = "dor_missing: <chaves>"`). `reimport` não reavalia. Log `key=value` sem texto do ticket.
- `daemon/.../ProjectRegistry.kt`: `POLICY_KEYS` ganha `"dor"`.
- `daemon/.../Protocol.kt` e `McpServerFactory.kt`: expor `dor` em `policies`/`policiesJson`.

## Testes de aceitação (QA)
`CardImportTest` (AC-1, 2, 4, 5) e `ProjectPoliciesTest` (AC-3).

# Referência externa e spec — spec

**Issue:** #11 · **Branch:** `feat/11-referencia-externa-e-spec` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** Importar e fechar exige saber de onde o card veio, e a spec precisa de um hash do texto para detectar edição feita fora da fábrica.
**Audience:** O desenvolvedor dentro de uma empresa (Jira, GitHub: importa e fecha o item de origem, detecta spec alterada por fora); também o desenvolvedor solo que importa issues de vários projetos.
**Acceptance criteria**
- **AC-1** — Dado uma referência externa, quando for criada, então ela guarda provedor, id externo, url, data de importação e hash do texto.
- **AC-2** — Dado o mesmo texto, quando o hash for calculado duas vezes, então o resultado é o mesmo; dado um texto com um caractere diferente, então o hash muda.
- **AC-3** — Dado um card com uma referência, quando se adicionar outra com provedor ou id externo diferente, então o card passa a ter as duas.
- **AC-4** — Dado um card, quando se adicionar uma referência com o mesmo par provedor e id externo de uma já existente, então a operação é recusada com erro que cita o provedor e o id.
**Out of scope:** Chamadas a provedores (importar e fechar de fato); persistência das referências; decidir o que fazer quando o hash difere.
**Product fit:** Serve ao posicionamento "importar e fechar, sem sincronização contínua": a referência é o elo mínimo com o tracker externo, e o hash sustenta a fábrica como dona do estado da spec. Não contradiz posicionamento nem escopo excluído.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada única: `core` (pacote `sfv2.core`), sem IO. Nenhum endpoint, evento externo ou migração.
- Contratos novos:
  - `ExternalRef(provider: String, externalId: String, url: String, importedAt: Instant, textHash: TextHash)` — data class imutável; o instante entra por parâmetro.
  - `TextHash` (value class sobre `String`) com `TextHash.of(text: String): TextHash` — SHA-256 do texto em UTF-8, em hexadecimal minúsculo; função pura.
  - `Card` ganha `references: List<ExternalRef>` (padrão vazia) e `Card.withReference(ref): Card` (devolve cópia); par `provider` + `externalId` repetido lança `DuplicateExternalRefException(provider, externalId) : RuntimeException`.
- Arquivos: `core/src/main/kotlin/sfv2/core/ExternalRef.kt` (novo), `Card.kt` (alterado).
- Migração: n/a (sem persistência).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/CardTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Hash SHA-256 via `java.security.MessageDigest` (JDK): sem dependência nova e estável entre execuções.
- Hash é do texto como recebido, sem normalização: qualquer caractere diferente muda o hash, como o AC-2 pede.
- Referências vivem no `Card` (lista imutável, unicidade validada em `withReference`): o AC-3 fala de "um card" e o `core` não tem persistência.
- Sem arquivo de teste novo: `CardTest.kt` cobre o card e as referências; `ExternalRef` é parte do modelo do card.
- Peso `light`: uma camada, 4 ACs, 2 arquivos, sem UI nem segurança.

## Open questions
- none

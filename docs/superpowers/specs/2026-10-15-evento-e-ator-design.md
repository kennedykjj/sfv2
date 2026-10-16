# Evento e ator — spec

**Issue:** #9 · **Branch:** `feat/9-evento-e-ator` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** Sem um registro único de "quem fez o quê e quando", a fábrica não tem auditoria nem prova de que o dono aprovou. Todo efeito precisa virar um evento com ator identificado.
**Audience:** O desenvolvedor dentro de uma empresa (trilha de auditoria, aprovação assinada); também o desenvolvedor solo, que precisa saber o que a fábrica fez sem ele.
**Acceptance criteria**
- **AC-1** — Dado um evento, quando for criado, então ele tem id, instante, ator, tipo e carga.
- **AC-2** — Dado os atores dono, agente (com papel), adaptador (com provedor) e fábrica (com componente), quando serializados em JSON e lidos de volta, então são iguais aos originais.
- **AC-3** — Dado um tipo de evento desconhecido na leitura, quando o evento for lido, então a leitura falha com erro que cita o tipo, em vez de descartar o evento.
**Out of scope:** Persistência (armazenamento, consulta e assinatura de eventos); catálogo completo de tipos de evento além do necessário para os testes.
**Product fit:** Serve ao princípio "toda ação é um evento com ator; a aprovação do dono é um evento assinado". Não contradiz posicionamento nem escopo excluído.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Camada única: `core` (pacote `sfv2.core`), sem IO. Nenhum endpoint, evento externo ou migração.
- Contratos novos:
  - `Event(id: EventId, at: Instant, actor: Actor, type: EventType, payload: Map<String, String>)` — data class imutável; o instante entra por parâmetro.
  - `sealed interface Actor`: `Owner`, `Agent(role: String)`, `Adapter(provider: String)`, `Factory(component: String)`.
  - `enum EventType` com tipos iniciais mínimos (`CARD_CREATED`, `CARD_MOVED`, `OWNER_APPROVED`) e `UnknownEventTypeException(type: String) : RuntimeException`.
  - `EventJson.encode(event): String` e `EventJson.decode(json): Event` (ator e tipo viajam como `{"kind": ...}` / string).
- Arquivos: `core/src/main/kotlin/sfv2/core/Event.kt`, `Actor.kt`, `EventJson.kt`; `core/build.gradle.kts` (dependência); `gradle/libs.versions.toml` (versão fixa).
- Migração: n/a (sem persistência).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/EventTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/EventTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/EventTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- JSON com Jackson (`jackson-databind`, versão fixa no catálogo), montando/lendo a árvore à mão: o plugin do kotlinx.serialization exigiria mexer em `build-logic/`, vedado.
- `EventType` é enum e a leitura de valor fora dele lança `UnknownEventTypeException` (falha explícita, AC-3); o catálogo completo de tipos fica para os cards que os usam.
- `payload` é `Map<String, String>`: simples e serializável; evolui quando um card exigir carga tipada.
- Um único arquivo de teste novo `EventTest.kt`: o módulo só tem `ModuleTest.kt`, que cobre o placeholder, não evento.
- Peso `light`: uma camada, 3 ACs, 4 arquivos, sem UI nem segurança.

## Open questions
- none

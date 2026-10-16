# Evento e ator — plano (#9)

**Spec:** `docs/superpowers/specs/2026-10-15-evento-e-ator-design.md` · **Peso:** light

## Tarefas

### T1 — core: evento, ator, serialização JSON (camada `core`, agente `engineer`)

Dependências: nenhuma. Única camada, sem UI, sem migração.

Arquivos:
- `gradle/libs.versions.toml`: versão fixa `jackson = "2.18.2"` e biblioteca `jackson-databind` (`com.fasterxml.jackson.core:jackson-databind`, `version.ref = "jackson"`).
- `core/build.gradle.kts`: `implementation(libs.jackson.databind)` (dependência de produção do módulo).
- `core/src/main/kotlin/sfv2/core/Actor.kt`: `sealed interface Actor` com `Owner`, `Agent(role)`, `Adapter(provider)`, `Factory(component)`.
- `core/src/main/kotlin/sfv2/core/Event.kt`: `EventId` (valor), `Event` (data class imutável com `id`, `at: Instant`, `actor`, `type`, `payload: Map<String, String>`), `enum EventType` (`CARD_CREATED`, `CARD_MOVED`, `OWNER_APPROVED`) e `UnknownEventTypeException : RuntimeException` que cita o tipo.
- `core/src/main/kotlin/sfv2/core/EventJson.kt`: `encode(event): String` e `decode(json): Event`. Ator como `{"kind": ...}`, tipo como string. Montagem e leitura da árvore à mão com Jackson. Tipo desconhecido na leitura lança `UnknownEventTypeException`.
- `core/src/test/kotlin/sfv2/core/EventTest.kt`: escrito pela QA (passo 3), não por este plano.

Critérios cobertos: AC-1 (campos do evento), AC-2 (ida e volta JSON de cada tipo de ator), AC-3 (tipo desconhecido falha, citando o tipo).

Observações para a implementação:
- `Instant` entra por parâmetro; nada de relógio do sistema.
- Nenhum `!!`; valores de erro com a mensagem citando o tipo e o id.
- Sem IO: só Jackson em memória (`readTree` / `ObjectMapper` sobre `String`).

# Referência externa e spec — plano (#11)

**Spec:** `docs/superpowers/specs/2026-10-15-referencia-externa-e-spec-design.md`
**Peso:** light · **Camada única:** `core` · **Agente:** solo (Tech Lead)

## Tarefas

### T1 — `core`: `TextHash` e `ExternalRef` (AC-1, AC-2)

- Arquivo novo: `core/src/main/kotlin/sfv2/core/ExternalRef.kt`
  - `@JvmInline value class TextHash(val hex: String)` com `companion object { fun of(text: String): TextHash }`: SHA-256 de `text.toByteArray(Charsets.UTF_8)` via `java.security.MessageDigest`, hexadecimal minúsculo. Função pura, sem IO.
  - `data class ExternalRef(val provider: String, val externalId: String, val url: String, val importedAt: Instant, val textHash: TextHash)`; `importedAt` entra por parâmetro (`java.time.Instant`), sem relógio do sistema.
  - `class DuplicateExternalRefException(val provider: String, val externalId: String) : RuntimeException(...)`, mensagem cita provedor e id.
- Dependências: nenhuma.

### T2 — `core`: `Card` com referências (AC-3, AC-4)

- Arquivo alterado: `core/src/main/kotlin/sfv2/core/Card.kt`
  - `data class Card` ganha `references: List<ExternalRef>` no construtor privado, padrão `emptyList()`; `create` não muda de assinatura.
  - `fun withReference(ref: ExternalRef): Card`: se já existir referência com o mesmo `provider` + `externalId`, lança `DuplicateExternalRefException`; senão devolve `Card(title, column, references + ref)`.
  - Sem `copy` público: a mudança passa pelo construtor privado, como já é o padrão do arquivo.
- Dependências: T1.

### T3 — Testes (QA)

- QA escreve os testes de aceitação em `core/src/test/kotlin/sfv2/core/CardTest.kt` (arquivo existente, não criar outro).
- Nota para QA: `CardTest.kt` já tem testes nomeados `AC-1`…`AC-4` de outra issue (#96). Os novos devem deixar claro a qual issue/AC pertencem, sem colidir com os nomes existentes.

## Ordem e arquivos

1. T1 (`ExternalRef.kt`), commit `feat(core): referência externa e hash do texto`
2. T2 (`Card.kt`), commit `feat(core): card com referências externas`

Testes de QA entram antes, no commit de aceitação, conforme a fábrica.

## Fora do plano

- Persistência, migração, chamadas a provedores, UI, segurança: n/a pela spec.

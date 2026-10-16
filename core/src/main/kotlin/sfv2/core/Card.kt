package sfv2.core

class CardValidationException(message: String) : RuntimeException(message)

data class Card private constructor(
    val title: String,
    val column: Column,
    val references: List<ExternalRef> = emptyList(),
) {

    fun withReference(ref: ExternalRef): Card {
        val duplicate = references.any { it.provider == ref.provider && it.externalId == ref.externalId }
        if (duplicate) {
            throw DuplicateExternalRefException(ref.provider, ref.externalId)
        }
        return Card(title, column, references + ref)
    }

    companion object {
        fun create(title: String, column: Column? = null): Card {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) {
                throw CardValidationException("Card title must not be blank (field: title)")
            }
            return Card(trimmed, column ?: Column.INBOX)
        }
    }
}

package sfv2.core

class CardValidationException(message: String) : RuntimeException(message)

/** A card is only built through [create]; copy() stays private so it cannot skip the title check. */
@ConsistentCopyVisibility
data class Card private constructor(val title: String, val column: Column) {

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

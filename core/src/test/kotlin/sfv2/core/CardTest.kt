package sfv2.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CardTest {

    @Test
    fun `AC-1 card created without column starts in Inbox`() {
        val card = Card.create("Write the spec")

        assertThat(card.column).isEqualTo(Column.INBOX)
    }

    @Test
    fun `AC-2 columns are listed in board order with display names`() {
        assertThat(Column.entries).containsExactly(
            Column.INBOX,
            Column.READY_FOR_REFINEMENT,
            Column.REFINEMENT,
            Column.SPEC_REVIEW,
            Column.READY_FOR_DEV,
            Column.CODING,
            Column.VALIDATION,
            Column.STAGED,
            Column.READY_FOR_PROD,
            Column.DONE,
        )
        assertThat(Column.READY_FOR_REFINEMENT.displayName).isEqualTo("Ready for Refinement")
    }

    @Test
    fun `AC-3 blank title is rejected with a validation error naming the title field`() {
        listOf("", "   ", "\t \n").forEach { blank ->
            val error = assertThrows<CardValidationException> { Card.create(blank) }

            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).contains("title")
        }
    }

    @Test
    fun `AC-4 title surrounding spaces are trimmed`() {
        val card = Card.create("  Write the spec  ")

        assertThat(card.title).isEqualTo("Write the spec")
    }
}

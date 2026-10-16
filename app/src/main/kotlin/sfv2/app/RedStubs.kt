package sfv2.app

import java.time.Clock
import kotlinx.serialization.json.JsonElement
import sfv2.core.Column

// Temporary red-gate stubs: signatures only, neutral bodies. The engineer replaces them and empties this file.

/** Reads one `card.list` row, tolerating a daemon that does not send the newer fields. */
fun parseBoardCard(element: JsonElement?): BoardCard? = null

/** What a board card says about itself: texts built only from the column, numbers and ids, never from the title. */
data class CardFace(
    val stage: String,
    val age: String?,
    val cost: String,
    val costNote: String?,
    val blocked: Boolean,
    val blockedLabel: String?,
    val description: String,
) {
    companion object {
        fun of(card: BoardCard, clock: Clock): CardFace = CardFace("", null, "", null, false, null, "")
    }
}

enum class StageHue { REFINE, DEVELOP, VALIDATE, OK, ACCENT, NEUTRAL }

fun stageHue(column: Column): StageHue = StageHue.NEUTRAL

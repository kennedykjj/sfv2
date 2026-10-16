package sfv2.app

import java.time.Instant
import sfv2.core.AssistantState

// shipfloor:qa-stub - lets PopupSkinTest compile for the red gate; the implementer deletes this file.
enum class SkinMode { NORMAL, SILENT }

enum class MotionPreference { FULL, REDUCED }

enum class PopupPhase { ENTRY, STAY, EXIT, GONE }

data class PopupCommand(
    val state: AssistantState,
    val phrase: String,
    val emittedAt: Instant,
    val motion: MotionPreference,
)

class PopupSkin {
    data class Update(val skin: PopupSkin, val command: PopupCommand?)

    val current: PopupCommand? = null

    fun onState(
        state: AssistantState,
        phrase: String,
        mode: SkinMode,
        motion: MotionPreference,
        now: Instant,
    ): Update = Update(this, null)

    fun phase(now: Instant): PopupPhase = PopupPhase.GONE
}

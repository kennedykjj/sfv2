package sfv2.app

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import sfv2.core.Column

// shipfloor:qa-stub — inert API so the acceptance tests compile and fail by assertion; the implementer deletes this file.

interface TrayPort {
    fun show(waiting: Int)

    fun clear()

    fun onActivated(listener: () -> Unit)
}

interface NotificationPort {
    fun notify(title: String, body: String, replacesId: Int): Int

    fun onActivated(listener: (notificationId: Int) -> Unit)
}

enum class NoticeLanguage { PT_BR, EN }

data class WaitingCard(val id: String, val column: Column, val since: Instant, val externalId: String?)

class WaitingCards private constructor() {
    val ordered: List<WaitingCard> = emptyList()
    val count: Int = 0

    fun contains(id: String): Boolean = false

    fun withSnapshot(cards: List<BoardCard>, now: Instant): WaitingCards = this

    fun withEvent(event: BoardEvent, fetched: BoardCard?, now: Instant): WaitingCards = this

    companion object {
        val EMPTY = WaitingCards()
    }
}

object NoticeText {
    const val TITLE = "Shipfloor"

    fun label(card: WaitingCard): String = ""

    fun body(cards: List<WaitingCard>, language: NoticeLanguage): String = ""

    fun tooltip(count: Int, language: NoticeLanguage): String = ""
}

sealed interface NoticeDecision {
    data object None : NoticeDecision

    data object Clear : NoticeDecision

    data class Show(val cards: List<WaitingCard>, val notify: Boolean) : NoticeDecision
}

class OwnerNoticePolicy {
    fun decide(waiting: WaitingCards, quiet: Boolean): NoticeDecision = NoticeDecision.None
}

object NoticeClick {
    fun target(cited: List<String>, waiting: WaitingCards): String? = null
}

class OwnerNoticeShell(
    tray: TrayPort,
    notifications: NotificationPort,
    clock: Clock,
    zone: ZoneId,
    language: NoticeLanguage,
    log: (String, Map<String, String>) -> Unit,
    open: (String?) -> Unit,
) {
    fun onSnapshot(cards: List<BoardCard>) = Unit

    fun onEvent(event: BoardEvent, fetched: BoardCard?) = Unit

    fun tick() = Unit
}

package sfv2.daemon

import java.time.Clock
import java.util.UUID
import sfv2.core.Card
import sfv2.core.CardValidationException
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.MoveDecision
import sfv2.core.MoveRules
import sfv2.store.CardEventStore

/** Applies the core rules to card commands and writes through the store. A refusal writes nothing. */
class CardCommands(
    private val store: CardEventStore,
    private val clock: Clock,
    private val ownerProof: OwnerProof,
    private val log: DaemonLog = StderrDaemonLog,
) {

    /** Serialized so the read-check-write of a move cannot interleave with another command. */
    @Synchronized
    fun handle(request: Request): Response = when (request) {
        is Request.CardCreate -> create(request)
        is Request.CardMove -> move(request)
        Request.CardList -> Response.CardsResult(store.cards().map { CardView(it.id, it.card.title, it.card.column) })
        is Request.CardShow -> show(request.id)
        Request.Ping, is Request.Rejected -> Response.Error("not a card command")
    }

    private fun create(request: Request.CardCreate): Response {
        val card = try {
            Card.create(request.title, Column.INBOX)
        } catch (e: CardValidationException) {
            return Response.Error(e.message ?: "invalid card")
        }
        val id = UUID.randomUUID().toString()
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = request.actor,
            type = EventType.CARD_CREATED,
            payload = mapOf("title" to card.title),
        )
        store.recordChange(id, card, event)
        log.log("card_command", mapOf("cmd" to "card.create", "card" to id))
        return Response.CardResult(CardView(id, card.title, card.column))
    }

    private fun move(request: Request.CardMove): Response {
        val current = store.card(request.id) ?: return notFound(request.id)
        when (val decision = MoveRules.check(current.column, request.to, request.actor)) {
            is MoveDecision.Refused -> {
                log.log("card_command_refused", mapOf("cmd" to "card.move", "card" to request.id))
                return Response.Error(decision.reason)
            }
            MoveDecision.Allowed -> Unit
        }
        val moved = Card.create(current.title, request.to)
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = request.actor,
            type = EventType.CARD_MOVED,
            payload = mapOf("from" to current.column.name, "to" to request.to.name),
        )
        store.recordChange(request.id, moved, event)
        log.log("card_command", mapOf("cmd" to "card.move", "card" to request.id))
        return Response.CardResult(CardView(request.id, moved.title, moved.column))
    }

    private fun show(id: String): Response {
        val card = store.card(id) ?: return notFound(id)
        return Response.CardResult(CardView(id, card.title, card.column))
    }

    private fun notFound(id: String): Response = Response.Error("card not found: $id")
}

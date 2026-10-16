package sfv2.daemon

import java.time.Clock
import java.time.Duration
import java.util.UUID
import sfv2.core.Actor
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
    subscriberQueueLimit: Int = EventStream.DEFAULT_QUEUE_LIMIT,
    closeGrace: Duration = EventStream.DEFAULT_CLOSE_GRACE,
) {

    /** Live feed of every recorded change. */
    val events: EventStream = EventStream(store, subscriberQueueLimit, log, clock, closeGrace)

    /** The only writer of events; shared with the run pool and the gate executor. */
    val recorder: EventRecorder = EventRecorder(store, events)

    /** Serialized so the read-check-write of a move cannot interleave with another command. */
    @Synchronized
    fun handle(request: Request): Response = when (request) {
        is Request.CardCreate -> create(request)
        is Request.CardMove -> move(request)
        is Request.CardApprove -> approve(request)
        is Request.CardRequestChange -> requestChange(request)
        Request.CardList -> Response.CardsResult(store.cards().map { CardView(it.id, it.card.title, it.card.column) })
        is Request.CardShow -> show(request.id)
        Request.ReportCsv -> Response.ReportResult(CardReport.toCsv(CardReport.build(store.cards()) { store.events(it) }))
        Request.Ping, is Request.Rejected, is Request.Subscribe, is Request.ProjectAdd, Request.ProjectList, is Request.CardRebuild, is Request.CardImport, is Request.CardImportBoard, is Request.CardCloseDraft, is Request.CardCloseConfirm -> Response.Error("not a card command")
    }

    private fun create(request: Request.CardCreate): Response {
        if (request.actor == Actor.Owner && !ownerProof.matches(request.proof)) {
            log.log("card_command_refused", mapOf("cmd" to "card.create"))
            return Response.Error(OWNER_PROOF_ERROR)
        }
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
        recorder.record(id, card, event)
        log.log("card_command", mapOf("cmd" to "card.create", "card" to id))
        return Response.CardResult(CardView(id, card.title, card.column))
    }

    private fun move(request: Request.CardMove): Response {
        if (request.actor == Actor.Owner && !ownerProof.matches(request.proof)) {
            log.log("card_command_refused", mapOf("cmd" to "card.move", "card" to request.id))
            return Response.Error(OWNER_PROOF_ERROR)
        }
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
        recorder.record(request.id, moved, event)
        log.log("card_command", mapOf("cmd" to "card.move", "card" to request.id))
        return Response.CardResult(CardView(request.id, moved.title, moved.column))
    }

    private fun approve(request: Request.CardApprove): Response =
        reviewDecision(
            cmd = "card.approve",
            actor = request.actor,
            proof = request.proof,
            id = request.id,
            to = Column.READY_FOR_DEV,
            type = EventType.OWNER_APPROVED,
            note = null,
        )

    private fun requestChange(request: Request.CardRequestChange): Response {
        val note = request.note
        if (note == null || note.isBlank()) {
            return Response.Error("note is required to request a change")
        }
        if (note.toByteArray(Charsets.UTF_8).size > MAX_NOTE_BYTES) {
            return Response.Error("note exceeds the $MAX_NOTE_BYTES byte limit")
        }
        return reviewDecision(
            cmd = "card.request-change",
            actor = request.actor,
            proof = request.proof,
            id = request.id,
            to = Column.READY_FOR_REFINEMENT,
            type = EventType.CARD_MOVED,
            note = note,
        )
    }

    /** Owner decision on a card waiting in Spec Review: proof, card and column are checked before anything is written. */
    private fun reviewDecision(
        cmd: String,
        actor: Actor,
        proof: String?,
        id: String,
        to: Column,
        type: EventType,
        note: String?,
    ): Response {
        if (actor != Actor.Owner || !ownerProof.matches(proof)) {
            log.log("card_command_refused", mapOf("cmd" to cmd, "card" to id))
            return Response.Error(OWNER_PROOF_ERROR)
        }
        val current = store.card(id) ?: return notFound(id)
        if (current.column != Column.SPEC_REVIEW) {
            log.log("card_command_refused", mapOf("cmd" to cmd, "card" to id))
            return Response.Error("card $id is in ${current.column.name}, not in SPEC_REVIEW")
        }
        when (val decision = MoveRules.check(current.column, to, actor)) {
            is MoveDecision.Refused -> {
                log.log("card_command_refused", mapOf("cmd" to cmd, "card" to id))
                return Response.Error(decision.reason)
            }
            MoveDecision.Allowed -> Unit
        }
        val moved = Card.create(current.title, to)
        val payload = buildMap {
            put("from", current.column.name)
            put("to", to.name)
            if (note != null) put("note", note)
        }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = actor,
            type = type,
            payload = payload,
        )
        recorder.record(id, moved, event)
        log.log("card_command", mapOf("cmd" to cmd, "card" to id))
        return Response.CardResult(CardView(id, moved.title, moved.column))
    }

    private fun show(id: String): Response {
        val card = store.card(id) ?: return notFound(id)
        return Response.CardResult(CardView(id, card.title, card.column))
    }

    private fun notFound(id: String): Response = Response.Error("card not found: $id")

    private companion object {
        const val MAX_NOTE_BYTES = 2048
        const val OWNER_PROOF_ERROR = "owner proof missing or invalid"
    }
}

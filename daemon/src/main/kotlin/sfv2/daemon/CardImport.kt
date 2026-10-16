package sfv2.daemon

import java.time.Clock
import java.util.UUID
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.ExternalRef
import sfv2.core.TextHash
import sfv2.ports.Tracker
import sfv2.ports.TrackerException
import sfv2.ports.TrackerItem
import sfv2.ports.TrackerItemState
import sfv2.store.CardEventStore
import sfv2.store.StoredCard

/**
 * Imports issues from the project's tracker into cards (`card.import`, owner only), by id or by search.
 * Issue text is untrusted data: it only becomes a normalized title and a hash, and is never logged.
 */
class CardImport private constructor(
    private val store: CardEventStore,
    private val recorder: EventRecorder,
    private val registry: ProjectRegistry,
    private val trackerOf: (Project) -> Tracker,
    private val ownerProof: OwnerProof,
    private val clock: Clock,
    private val log: DaemonLog,
) {
    constructor(
        store: CardEventStore,
        recorder: EventRecorder,
        registry: ProjectRegistry,
        tracker: Tracker,
        ownerProof: OwnerProof,
        clock: Clock,
        log: DaemonLog,
    ) : this(store, recorder, registry, { tracker }, ownerProof, clock, log)

    /** Serialized so two imports cannot both create the card of the same issue. */
    @Synchronized
    fun handle(request: Request): Response {
        if (request !is Request.CardImport) return Response.Error("not an import command")
        if (request.actor != Actor.Owner || !ownerProof.matches(request.proof)) {
            log.log("card_command_refused", mapOf("cmd" to CMD))
            return Response.Error(OWNER_PROOF_ERROR)
        }
        if ((request.id == null) == (request.query == null)) {
            return Response.Error("exactly one of id and query is required")
        }
        val project = when (val chosen = chooseProject(request.project)) {
            is Chosen.Found -> chosen.project
            is Chosen.Failed -> return Response.Error(chosen.message)
        }
        val tracker = trackerOf(project)
        return if (request.id != null) importById(tracker, project, request.id) else importQuery(tracker, project, requireNotNull(request.query))
    }

    private fun importById(tracker: Tracker, project: Project, id: String): Response {
        val item = try {
            tracker.fetch(id)
        } catch (e: TrackerException) {
            log.log("card_import_failed", mapOf("cmd" to CMD, "operation" to "fetch", "externalId" to id))
            return Response.Error("fetch failed for issue $id")
        } ?: return Response.Error("issue not found: $id")
        return Response.CardsResult(listOf(upsert(item, project)))
    }

    private fun importQuery(tracker: Tracker, project: Project, query: String): Response {
        val found = try {
            tracker.search(query)
        } catch (e: TrackerException) {
            log.log("card_import_failed", mapOf("cmd" to CMD, "operation" to "search"))
            return Response.Error("search failed")
        }
        val created = found
            .filter { it.state == TrackerItemState.OPEN && existing(project, it.id) == null }
            .map { create(it, project) }
        log.log("card_import", mapOf("cmd" to CMD, "created" to created.size.toString(), "found" to found.size.toString()))
        return Response.CardsResult(created)
    }

    private fun upsert(item: TrackerItem, project: Project): CardView {
        val current = existing(project, item.id)
        return if (current == null) create(item, project) else reimport(item, current)
    }

    private fun existing(project: Project, externalId: String): StoredCard? =
        store.cards().firstOrNull { stored ->
            store.projectOf(stored.id) == project.repo &&
                stored.card.references.any { it.provider == PROVIDER && it.externalId == externalId }
        }

    private fun create(item: TrackerItem, project: Project): CardView {
        val card = Card.create(normalizeTitle(item.title.value, item.id), Column.INBOX).withReference(refOf(item))
        val id = UUID.randomUUID().toString()
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Adapter(PROVIDER),
            type = EventType.CARD_CREATED,
            payload = mapOf("title" to card.title, "origin" to "imported", "provider" to PROVIDER, "externalId" to item.id),
        )
        recorder.record(id, card, event)
        store.assignProject(id, project.repo)
        log.log("card_imported", mapOf("card" to id, "externalId" to item.id))
        return CardView(id, card.title, card.column)
    }

    private fun reimport(item: TrackerItem, current: StoredCard): CardView {
        val old = current.card
        val oldRef = old.references.first { it.provider == PROVIDER && it.externalId == item.id }
        val changed = oldRef.textHash != TextHash(item.textHash)
        val frozen = changed && old.column !in EDITABLE_TITLE_COLUMNS
        val payload = mutableMapOf("provider" to PROVIDER, "externalId" to item.id, "changed" to changed.toString())
        var card = old
        if (changed) {
            val title = if (frozen) old.title else normalizeTitle(item.title.value, item.id)
            card = old.references.fold(Card.create(title, old.column)) { acc, ref ->
                acc.withReference(if (ref === oldRef) refOf(item) else ref)
            }
            if (frozen) payload["title_frozen"] = "true" else payload["title"] = title
        }
        val event = Event(
            id = EventId(UUID.randomUUID().toString()),
            at = clock.instant(),
            actor = Actor.Adapter(PROVIDER),
            type = EventType.CARD_REIMPORTED,
            payload = payload,
        )
        recorder.record(current.id, card, event)
        if (changed) {
            TextChangeWarning.warnIfChanged(
                store, recorder, clock, current.id, card, item.id, oldRef.textHash.value, item.textHash,
            )
        }
        log.log("card_reimported", mapOf("card" to current.id, "externalId" to item.id, "changed" to changed.toString()))
        return CardView(current.id, card.title, card.column)
    }

    private fun refOf(item: TrackerItem): ExternalRef =
        ExternalRef(PROVIDER, item.id, item.url, clock.instant(), TextHash(item.textHash))

    private fun chooseProject(name: String?): Chosen {
        val projects = registry.list()
        if (name != null) {
            val found = projects.firstOrNull { it.name == name }
            return if (found != null) Chosen.Found(found) else Chosen.Failed("project not found: $name")
        }
        return when (projects.size) {
            0 -> Chosen.Failed("no project registered")
            1 -> Chosen.Found(projects.single())
            else -> Chosen.Failed("several projects registered: choose one with --project")
        }
    }

    private sealed interface Chosen {
        data class Found(val project: Project) : Chosen
        data class Failed(val message: String) : Chosen
    }

    companion object {
        private const val CMD = "card.import"
        private const val PROVIDER = "github"
        private const val MAX_TITLE = 200
        private const val OWNER_PROOF_ERROR = "owner proof missing or invalid"
        private val EDITABLE_TITLE_COLUMNS = setOf(
            Column.INBOX,
            Column.READY_FOR_REFINEMENT,
            Column.REFINEMENT,
            Column.SPEC_REVIEW,
            Column.READY_FOR_DEV,
        )

        /** Production wiring: the tracker is the one of the project being imported into. */
        fun forProjects(
            store: CardEventStore,
            recorder: EventRecorder,
            registry: ProjectRegistry,
            trackerOf: (Project) -> Tracker,
            ownerProof: OwnerProof,
            clock: Clock,
            log: DaemonLog,
        ): CardImport = CardImport(store, recorder, registry, trackerOf, ownerProof, clock, log)

        /** Control characters and whitespace runs become one space, cut at 200 characters; empty becomes `Issue <id>`. */
        internal fun normalizeTitle(raw: String, id: String): String {
            val spaced = raw.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("")
            val collapsed = spaced.replace(Regex(" {2,}"), " ").trim()
            return collapsed.take(MAX_TITLE).trimEnd().ifEmpty { "Issue $id" }
        }
    }
}

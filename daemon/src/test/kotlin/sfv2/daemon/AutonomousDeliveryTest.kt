package sfv2.daemon

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sfv2.core.Actor
import sfv2.core.Card
import sfv2.core.Column
import sfv2.core.Event
import sfv2.core.EventId
import sfv2.core.EventType
import sfv2.core.ProjectPolicies
import sfv2.ports.ChecksState
import sfv2.ports.FailedCheck
import sfv2.ports.FakeForge
import sfv2.ports.PullRequestRef
import sfv2.ports.PullRequestRequest
import sfv2.store.CardEventStore
import sfv2.store.Database

class AutonomousDeliveryTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: EventRecorder
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
    private val forge = FakeForge()
    private var pauses = 0
    private var sequence = 0
    private val policies = ProjectPolicies.DEFAULT

    @BeforeEach
    fun open() {
        database = Database.open(dir.resolve("delivery.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = EventRecorder(store, stream)
    }

    @AfterEach
    fun close() {
        stream.close()
        database.close()
    }

    private fun delivery(maxPolls: Int = 3) = AutonomousDelivery(
        store, recorder, { forge }, { policies }, { pauses++ }, maxPolls, clock, DaemonLog { _, _ -> },
    )

    private fun record(cardId: String, column: Column, actor: Actor, type: EventType, payload: Map<String, String>) {
        val event = Event(EventId("e-${sequence++}"), clock.instant(), actor, type, payload)
        recorder.record(cardId, Card.create("Delivery", column), event)
    }

    private fun move(cardId: String, from: Column, to: Column, actor: Actor, extra: Map<String, String> = emptyMap()) =
        record(cardId, to, actor, EventType.CARD_MOVED, mapOf("from" to from.name, "to" to to.name) + extra)

    private fun approve(cardId: String) = move(cardId, Column.STAGED, Column.READY_FOR_PROD, Actor.Owner)

    /** A card the validate staged on the PR's current head and the owner moved to Ready for Prod. */
    private fun approvedCard(cardId: String): PullRequestRef {
        val pr = forge.openPullRequest(PullRequestRequest("feat/$cardId-delivery", "main", "Delivery", "body"))
        record(cardId, Column.VALIDATION, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Delivery"))
        move(cardId, Column.VALIDATION, Column.STAGED, Actor.Factory("validate"), mapOf("ci" to "green", "head" to pr.head))
        approve(cardId)
        return pr
    }

    private fun prMergedType(): EventType {
        val type = EventType.entries.firstOrNull { it.name == "PR_MERGED" }
        assertThat(type).`as`("EventType has PR_MERGED").isNotNull()
        return requireNotNull(type)
    }

    private fun merged(cardId: String) = store.events(cardId).filter { it.type.name == "PR_MERGED" }

    private fun column(cardId: String) = store.card(cardId)?.column

    private fun lastMove(cardId: String) = store.events(cardId).last { it.type == EventType.CARD_MOVED }

    private fun greenOn(pr: PullRequestRef) = forge.scriptChecks("merge-${pr.number}", ChecksState.Passed)

    private fun returned(outcome: DeliveryOutcome, cardId: String, vararg parts: String) {
        assertThat(outcome).isInstanceOf(DeliveryOutcome.ReturnedToOwner::class.java)
        val reason = (outcome as DeliveryOutcome.ReturnedToOwner).reason
        assertThat(reason).contains(*parts)
        assertThat(column(cardId)).isEqualTo(Column.STAGED)
        assertThat(lastMove(cardId).payload["to"]).isEqualTo("STAGED")
        assertThat(lastMove(cardId).actor).isEqualTo(Actor.Factory("deliver"))
        assertThat(lastMove(cardId).payload["reason"]).contains(*parts)
    }

    @Test
    fun `AC-1 merges the approved head and closes the card on a green main`() {
        val pr = approvedCard("c1")
        greenOn(pr)

        val outcome = delivery().deliver("c1")

        assertThat(outcome).isEqualTo(DeliveryOutcome.Done)
        assertThat(forge.merges.map { it.head }).containsExactly(pr.head)
        assertThat(column("c1")).isEqualTo(Column.DONE)
        assertThat(lastMove("c1").payload).containsEntry("merge_commit", "merge-${pr.number}").containsEntry("ci", "green")
        assertThat(lastMove("c1").actor).isEqualTo(Actor.Factory("deliver"))
    }

    @Test
    fun `AC-2 a head that is not the approved one is never merged and goes back to the owner with both shas`() {
        val pr = approvedCard("c1")
        forge.pushHead(pr, "sha-pushed-later")

        val outcome = delivery().deliver("c1")

        assertThat(forge.merges).isEmpty()
        returned(outcome, "c1", "head_mismatch", pr.head, "sha-pushed-later")
        assertThat(merged("c1")).isEmpty()
    }

    @Test
    fun `AC-3 conflict, closed PR and forge failure send the card back to the owner without a merge`() {
        val conflicted = approvedCard("c1")
        forge.scriptConflict(conflicted)
        returned(delivery().deliver("c1"), "c1", "merge_conflict")

        val closed = approvedCard("c2")
        forge.closeOutside(closed)
        returned(delivery().deliver("c2"), "c2", "pr_not_open")

        approvedCard("c3")
        forge.failNext("merge")
        returned(delivery().deliver("c3"), "c3", "forge_failed")

        assertThat(forge.merges).isEmpty()
        listOf("c1", "c2", "c3").forEach { assertThat(merged(it)).isEmpty() }
    }

    @Test
    fun `AC-4 only the green of the merge commit on main closes the card`() {
        val pr = approvedCard("c1")
        forge.scriptChecks(pr.head, ChecksState.Passed)

        val outcome = delivery().deliver("c1")

        returned(outcome, "c1", "main_ci_timeout")
        assertThat(merged("c1")).hasSize(1)
        assertThat(store.events("c1").none { it.type == EventType.CARD_MOVED && it.payload["to"] == "DONE" }).isTrue()
    }

    @Test
    fun `AC-5 a red main goes back to the owner with the failing check names and a capped summary`() {
        val pr = approvedCard("c1")
        forge.scriptChecks(
            "merge-${pr.number}",
            ChecksState.Failed(listOf(FailedCheck("unit-tests", "x".repeat(5000)))),
        )

        val outcome = delivery().deliver("c1")

        returned(outcome, "c1", "main_ci_red", "unit-tests")
        assertThat(lastMove("c1").payload.getValue("reason").toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(2048)
        assertThat(column("c1")).isNotEqualTo(Column.DONE)
    }

    @Test
    fun `AC-5 a main without result within the polls goes back to the owner`() {
        approvedCard("c1")

        val outcome = delivery(maxPolls = 3).deliver("c1")

        returned(outcome, "c1", "main_ci_timeout")
        assertThat(pauses).isGreaterThan(0)
    }

    @Test
    fun `AC-7 a merge already recorded is not repeated after a restart and only the main CI is followed`() {
        val pr = approvedCard("c1")
        record(
            "c1", Column.READY_FOR_PROD, Actor.Factory("deliver"), prMergedType(),
            mapOf("pr" to pr.number.toString(), "merge_commit" to "merge-${pr.number}", "head" to pr.head),
        )
        greenOn(pr)

        val outcome = delivery().deliver("c1")

        assertThat(outcome).isEqualTo(DeliveryOutcome.Done)
        assertThat(forge.merges).isEmpty()
        assertThat(merged("c1")).hasSize(1)
        assertThat(delivery().deliver("c1")).isEqualTo(DeliveryOutcome.Skipped)
        assertThat(merged("c1")).hasSize(1)
    }

    @Test
    fun `AC-8 an approval after a red main reuses the validate head, does not merge twice and closes on green`() {
        val pr = approvedCard("c1")
        forge.scriptChecks("merge-${pr.number}", ChecksState.Failed(listOf(FailedCheck("unit-tests", "boom"))))
        returned(delivery().deliver("c1"), "c1", "main_ci_red")

        greenOn(pr)
        approve("c1")
        val outcome = delivery().deliver("c1")

        assertThat(outcome).isEqualTo(DeliveryOutcome.Done)
        assertThat(forge.merges).hasSize(1)
        assertThat(merged("c1")).hasSize(1)
        assertThat(column("c1")).isEqualTo(Column.DONE)
    }

    @Test
    fun `AC-8 an approval after a head mismatch merges the validate head and ignores the return event`() {
        val pr = approvedCard("c1")
        forge.pushHead(pr, "sha-pushed-later")
        returned(delivery().deliver("c1"), "c1", "head_mismatch")

        forge.pushHead(pr, pr.head)
        greenOn(pr)
        approve("c1")
        val outcome = delivery().deliver("c1")

        assertThat(outcome).isEqualTo(DeliveryOutcome.Done)
        assertThat(forge.merges.map { it.head }).containsExactly(pr.head)
    }

    @Test
    fun `AC-8 a card without a validate head goes back to the owner as no_approved_head`() {
        forge.openPullRequest(PullRequestRequest("feat/c1-delivery", "main", "Delivery", "body"))
        record("c1", Column.STAGED, Actor.Owner, EventType.CARD_CREATED, mapOf("title" to "Delivery"))
        approve("c1")

        returned(delivery().deliver("c1"), "c1", "no_approved_head")

        assertThat(forge.merges).isEmpty()
    }
}

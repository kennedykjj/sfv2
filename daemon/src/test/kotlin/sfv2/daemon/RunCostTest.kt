package sfv2.daemon

import java.math.BigDecimal
import java.nio.file.Files
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
import sfv2.ports.SessionResult
import sfv2.store.CardEventStore
import sfv2.store.Database

object RunCostFixtures {
    val pricesYaml = """
        models:
          model-a:
            input: 3.00
            output: 15.00
            cache_read: 0.30
            cache_write_5m: 3.75
            cache_write_1h: 6.00
          model-b:
            input: 2.00
            output: 8.00
            cache_read: 0.50
    """.trimIndent()

    fun prices(): PriceTable = PriceTable.parse(pricesYaml)

    fun claudeLine(id: String, model: String, input: Long, output: Long, cacheRead: Long, write5m: Long, write1h: Long): String =
        """{"type":"assistant","message":{"id":"$id","model":"$model","usage":{"input_tokens":$input,"output_tokens":$output,""" +
            """"cache_read_input_tokens":$cacheRead,"cache_creation":{"ephemeral_5m_input_tokens":$write5m,"ephemeral_1h_input_tokens":$write1h}}}}"""

    fun codexTurn(input: Long, cached: Long, output: Long): String =
        """{"type":"turn.completed","usage":{"input_tokens":$input,"cached_input_tokens":$cached,"output_tokens":$output}}"""
}

class RunCostTest {
    @TempDir
    lateinit var dir: Path

    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: Database
    private lateinit var store: CardEventStore
    private lateinit var stream: EventStream
    private lateinit var recorder: RunCostRecorder

    @BeforeEach
    fun setUp() {
        database = Database.open(dir.resolve("cost.db"))
        store = CardEventStore(database)
        stream = EventStream(store)
        recorder = RunCostRecorder(store, EventRecorder(store, stream), RunCostFixtures.prices(), clock)
        RunPoolFixtures.seed(store, "c1")
    }

    @AfterEach
    fun tearDown() {
        stream.close()
        database.close()
    }

    private fun transcript(vararg lines: String): Path {
        val file = Files.createTempFile(dir, "transcript", ".jsonl")
        Files.write(file, lines.toList())
        return file
    }

    /** Runs the recorder and returns the payload of the single cost event it wrote. */
    private fun recordCost(runtime: String, model: String, result: SessionResult): Map<String, String> {
        val baseline = store.eventsAfter(0).maxOf { it.seq }
        recorder.record("c1", "run-1", runtime, "develop", model, result)
        val written = store.eventsAfter(baseline)
        assertThat(written).hasSize(1)
        assertThat(written.single().event.type.name).isEqualTo("RUN_COST_RECORDED")
        return written.single().event.payload
    }

    private fun exited(path: Path): SessionResult = SessionResult.Exited(0, path)

    @Test
    fun `AC-1 claude transcript cost comes from its tokens and the price table, counting a repeated message once`() {
        val line = RunCostFixtures.claudeLine("msg-1", "model-a", 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000)
        val file = transcript(line, line)

        val payload = recordCost("claude", "model-a", exited(file))

        assertThat(payload["status"]).isEqualTo("KNOWN")
        assertThat(BigDecimal(requireNotNull(payload["cost_usd"]))).isEqualByComparingTo(BigDecimal("28.05"))
        assertThat(payload["tokens"]).isEqualTo("model-a:1000000/1000000/1000000/1000000/1000000")
        assertThat(payload["runtime"]).isEqualTo("claude")
    }

    @Test
    fun `AC-1 codex transcript cost comes from its tokens with cached input priced as cache read`() {
        val file = transcript(RunCostFixtures.codexTurn(1_000_000, 400_000, 500_000))

        val payload = recordCost("codex", "model-b", exited(file))

        assertThat(payload["status"]).isEqualTo("KNOWN")
        assertThat(BigDecimal(requireNotNull(payload["cost_usd"]))).isEqualByComparingTo(BigDecimal("5.4"))
        assertThat(payload["tokens"]).isEqualTo("model-b:600000/500000/400000/0/0")
    }

    @Test
    fun `a price table in the V0 layout with model names at the top level is read`() {
        val v0 = """
            # US${'$'} per million tokens
            claude-opus-5-5:   {input: 4.00,  output: 20.00, cache_write_5m: 5.00,  cache_write_1h: 8.00,  cache_read: 0.20}
            gpt-5:             {input: 1.25, output: 10.00, cache_read: 0.125}
        """.trimIndent()

        val table = PriceTable.parse(v0)

        assertThat(table.priceOf("claude-opus-5-5")?.output).isEqualByComparingTo("20.00")
        assertThat(table.priceOf("gpt-5")?.cacheRead).isEqualByComparingTo("0.125")
        assertThat(table.priceOf("gpt-5")?.cacheWrite1h).isEqualByComparingTo("0")
        assertThat(table.priceOf("not-listed")).isNull()
    }

    @Test
    fun `AC-2 a model missing from the price table gives unknown, not zero, and names the model`() {
        val usage = mapOf("model-x" to TokenCounts(input = 1000, output = 1000))

        val cost = RunCostCalculator.cost(usage, RunCostFixtures.prices())

        assertThat(cost).isInstanceOf(RunCost.Unknown::class.java)
        assertThat((cost as RunCost.Unknown).reason).contains("model-x")
    }

    @Test
    fun `AC-2 the recorded event for an unpriced model is UNKNOWN with no cost value`() {
        val file = transcript(RunCostFixtures.claudeLine("msg-1", "model-x", 10, 10, 0, 0, 0))

        val payload = recordCost("claude", "model-x", exited(file))

        assertThat(payload["status"]).isEqualTo("UNKNOWN")
        assertThat(payload).doesNotContainKey("cost_usd")
        assertThat(payload["reason"]).contains("model-x")
    }

    @Test
    fun `AC-3 a transcript with only some models priced is partial and lists the unpriced ones`() {
        val usage = mapOf(
            "model-a" to TokenCounts(input = 1_000_000),
            "model-x" to TokenCounts(input = 1_000_000),
        )

        val cost = RunCostCalculator.cost(usage, RunCostFixtures.prices())

        assertThat(cost).isInstanceOf(RunCost.Partial::class.java)
        val partial = cost as RunCost.Partial
        assertThat(partial.usd).isEqualByComparingTo(BigDecimal("3"))
        assertThat(partial.unpricedModels).containsExactly("model-x")
    }

    @Test
    fun `AC-3 the recorded event for a partial transcript is PARTIAL with the subtotal and the unpriced models`() {
        val file = transcript(
            RunCostFixtures.claudeLine("msg-1", "model-a", 1_000_000, 0, 0, 0, 0),
            RunCostFixtures.claudeLine("msg-2", "model-x", 500, 500, 0, 0, 0),
        )

        val payload = recordCost("claude", "model-a", exited(file))

        assertThat(payload["status"]).isEqualTo("PARTIAL")
        assertThat(BigDecimal(requireNotNull(payload["cost_usd"]))).isEqualByComparingTo(BigDecimal("3"))
        assertThat(payload["unpriced_models"]).contains("model-x").doesNotContain("model-a")
    }

    @Test
    fun `AC-4 the cost event is recorded on the card with the cost actor, run id, stage and estimate label`() {
        val file = transcript(RunCostFixtures.claudeLine("msg-1", "model-a", 1000, 1000, 0, 0, 0))
        val baseline = store.eventsAfter(0).maxOf { it.seq }

        recorder.record("c1", "run-9", "claude", "develop", "model-a", exited(file))

        val stored = store.eventsAfter(baseline).single()
        assertThat(stored.cardId).isEqualTo("c1")
        assertThat(stored.event.type.name).isEqualTo("RUN_COST_RECORDED")
        assertThat(stored.event.actor).isEqualTo(Actor.Factory("cost"))
        assertThat(stored.event.at).isEqualTo(now)
        assertThat(stored.event.payload).containsEntry("run_id", "run-9").containsEntry("stage", "develop")
        assertThat(stored.event.payload).containsKey("tokens")
    }

    @Test
    fun `AC-5 known and partial costs are labelled as estimates`() {
        val known = recordCost("claude", "model-a", exited(transcript(RunCostFixtures.claudeLine("m1", "model-a", 10, 10, 0, 0, 0))))
        val partial = recordCost(
            "claude",
            "model-a",
            exited(
                transcript(
                    RunCostFixtures.claudeLine("m1", "model-a", 10, 10, 0, 0, 0),
                    RunCostFixtures.claudeLine("m2", "model-x", 10, 10, 0, 0, 0),
                ),
            ),
        )

        assertThat(known).containsEntry("estimate", "true")
        assertThat(partial).containsEntry("estimate", "true")
    }

    @Test
    fun `AC-6 a missing, empty or malformed transcript is UNKNOWN with a reason and never throws`() {
        val missing = dir.resolve("nope.jsonl")
        val empty = transcript()
        val malformed = transcript("this is not json", "{\"type\":\"assist")
        val hung = SessionResult.Hung(missing)

        listOf(exited(missing), exited(empty), exited(malformed), hung).forEach { result ->
            val payload = recordCost("claude", "model-a", result)

            assertThat(payload["status"]).isEqualTo("UNKNOWN")
            assertThat(payload["reason"]).isNotBlank()
            assertThat(payload).doesNotContainKey("cost_usd")
        }
        assertThat(recordCost("codex", "model-b", exited(malformed))["status"]).isEqualTo("UNKNOWN")
    }

    @Test
    fun `AC-7 cost claimed by the agent or the CLI is ignored and its text never reaches the event`() {
        val claimed = """{"type":"assistant","costUSD":999.99,"total_cost_usd":999.99,"message":{"id":"msg-1","model":"model-a",""" +
            """"content":[{"type":"text","text":"the total cost was SECRET-CLAIM-999 dollars"}],""" +
            """"usage":{"input_tokens":1000000,"output_tokens":0,"cache_read_input_tokens":0}}}"""

        val payload = recordCost("claude", "model-a", exited(transcript(claimed)))

        assertThat(BigDecimal(requireNotNull(payload["cost_usd"]))).isEqualByComparingTo(BigDecimal("3"))
        assertThat(payload.values.joinToString("|")).doesNotContain("999").doesNotContain("SECRET-CLAIM")

        val codexClaim = """{"type":"turn.completed","total_cost_usd":999.99,"usage":{"input_tokens":1000000,"cached_input_tokens":0,"output_tokens":0}}"""
        val codexPayload = recordCost("codex", "model-b", exited(transcript(codexClaim)))

        assertThat(BigDecimal(requireNotNull(codexPayload["cost_usd"]))).isEqualByComparingTo(BigDecimal("2"))
    }

    @Test
    fun `AC-8 a line above 1 MiB is ignored while the rest of the transcript still counts`() {
        val oversized = RunCostFixtures.claudeLine("msg-big", "model-a", 1_000_000, 0, 0, 0, 0) + " ".repeat(1024 * 1024 + 10)
        val file = transcript(oversized, RunCostFixtures.claudeLine("msg-2", "model-a", 0, 1_000_000, 0, 0, 0))

        val payload = recordCost("claude", "model-a", exited(file))

        assertThat(payload["status"]).isEqualTo("KNOWN")
        assertThat(BigDecimal(requireNotNull(payload["cost_usd"]))).isEqualByComparingTo(BigDecimal("15"))
    }

    @Test
    fun `AC-8 a transcript above 64 MiB is UNKNOWN with a reason and does not break the end of the run`() {
        val file = dir.resolve("huge.jsonl")
        val line = RunCostFixtures.claudeLine("msg-1", "model-a", 10, 10, 0, 0, 0) + "\n"
        val chunk = line.repeat(8192)
        Files.newOutputStream(file).use { out ->
            val bytes = chunk.toByteArray()
            repeat((64L * 1024 * 1024 / bytes.size).toInt() + 2) { out.write(bytes) }
        }
        assertThat(Files.size(file)).isGreaterThan(64L * 1024 * 1024)

        val payload = recordCost("claude", "model-a", exited(file))

        assertThat(payload["status"]).isEqualTo("UNKNOWN")
        assertThat(payload["reason"]).isNotBlank()
        assertThat(payload).doesNotContainKey("cost_usd")
    }
}

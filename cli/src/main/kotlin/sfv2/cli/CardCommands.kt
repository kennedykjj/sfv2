package sfv2.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import sfv2.core.Column

private const val DAEMON_DOWN_MESSAGE = "daemon não está rodando; inicie com `./gradlew :daemon:run`"
private const val EXIT_FAILURE = 1

/** What every command needs: where the daemon is and where to write. */
class CliEnvironment(
    val env: Map<String, String>,
    val out: (String) -> Unit,
    val err: (String) -> Unit,
) {
    /** Reports [message] on stderr and ends the command with exit code 1. */
    fun fail(message: String): Nothing {
        err(message)
        throw ProgramResult(EXIT_FAILURE)
    }

    fun location(): SocketLocation =
        resolveSocketLocation(env) ?: fail("XDG_RUNTIME_DIR não está definido; a CLI precisa dele para achar o socket do daemon")

    fun client(): DaemonClient = DaemonClient(location().socket)

    /** The owner token; fails before any connection is opened when it cannot be read. */
    fun ownerToken(): String {
        val path = location().token
        return readOwnerToken(path)
            ?: fail("token do dono ausente, ilegível ou vazio: $path; inicie o daemon (ele gera o arquivo)")
    }

    /** Sends [request] and returns the daemon's result, or fails with the right message. */
    fun call(request: JsonObject): JsonElement =
        when (val result = client().request(request)) {
            is DaemonResult.Ok -> result.result
            is DaemonResult.DaemonError -> fail(result.message)
            DaemonResult.Unavailable -> fail(DAEMON_DOWN_MESSAGE)
        }
}

private fun cardLine(card: JsonElement): String {
    val obj = card as? JsonObject ?: return card.toString()
    fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return "${field("id")}  ${field("column")}  ${field("title")}"
}

private fun parseColumn(text: String): Column? {
    val normalized = text.trim().uppercase().replace('-', '_')
    return Column.entries.firstOrNull { it.name == normalized }
}

class CardGroup : CliktCommand(name = "card") {
    override fun help(context: Context) = "Create, list, move and show cards."

    override fun run() = Unit
}

class CardCreate(private val cli: CliEnvironment) : CliktCommand(name = "create") {
    private val title by argument(help = "Card title")

    override fun help(context: Context) = "Create a card in the inbox."

    override fun run() {
        val token = cli.ownerToken()
        val request = buildJsonObject {
            put("cmd", "card.create")
            put("actor", "owner")
            put("title", title)
            put("proof", token)
        }
        cli.out(cardLine(cli.call(request)))
    }
}

class CardList(private val cli: CliEnvironment) : CliktCommand(name = "list") {
    override fun help(context: Context) = "List all cards."

    override fun run() {
        val cards = cli.call(buildJsonObject { put("cmd", "card.list") }) as? JsonArray
            ?: cli.fail("resposta inesperada do daemon")
        cards.forEach { cli.out(cardLine(it)) }
    }
}

class CardMove(private val cli: CliEnvironment) : CliktCommand(name = "move") {
    private val id by argument(help = "Card id")
    private val column by argument(help = "Target column, e.g. ready-for-dev").convert { text ->
        parseColumn(text) ?: fail("coluna desconhecida '$text'; válidas: ${Column.entries.joinToString(", ") { it.name }}")
    }

    override fun help(context: Context) = "Move a card to a column."

    override fun run() {
        val token = cli.ownerToken()
        val request = buildJsonObject {
            put("cmd", "card.move")
            put("actor", "owner")
            put("id", id)
            put("to", column.name)
            put("proof", token)
        }
        cli.out(cardLine(cli.call(request)))
    }
}

class CardShow(private val cli: CliEnvironment) : CliktCommand(name = "show") {
    private val id by argument(help = "Card id")

    override fun help(context: Context) = "Show one card."

    override fun run() {
        cli.out(cardLine(cli.call(buildJsonObject { put("cmd", "card.show"); put("id", id) })))
    }
}

class EventsCommand(private val cli: CliEnvironment) : CliktCommand(name = "events") {
    private val after by option("--after", help = "Replay events after this sequence number").long()
    private val count by option("--count", help = "Exit after this many events").int()

    override fun help(context: Context) = "Print the event stream as JSON lines."

    override fun run() {
        val limit = count
        if (limit != null && limit < 1) cli.fail("--count precisa ser pelo menos 1")
        val request = buildJsonObject {
            put("cmd", "events.subscribe")
            after?.let { put("after", it) }
        }
        var seen = 0
        val result = cli.client().stream(request) { line ->
            cli.out(line)
            seen++
            limit == null || seen < limit
        }
        when (result) {
            is StreamResult.Rejected -> cli.fail(result.message)
            is StreamResult.Ended -> when (val end = result.end) {
                StreamEnd.Stopped -> Unit
                is StreamEnd.Failed -> cli.fail(end.message)
                StreamEnd.Closed -> cli.fail("o daemon encerrou o stream de eventos")
                StreamEnd.Unavailable -> cli.fail(DAEMON_DOWN_MESSAGE)
            }
        }
    }
}

/** Registers the card group and the events command on [root]. */
fun Sfv2Command.withCardCommands(cli: CliEnvironment): Sfv2Command =
    subcommands(
        CardGroup().subcommands(CardCreate(cli), CardList(cli), CardMove(cli), CardShow(cli)),
        EventsCommand(cli),
    ) as Sfv2Command

package sfv2.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private fun projectField(project: JsonObject, name: String): String =
    (project[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun projectLine(project: JsonElement): String {
    val obj = project as? JsonObject ?: return project.toString()
    return "${projectField(obj, "name")}  ${projectField(obj, "repo")}"
}

/** The path the daemon registers; a path that does not resolve goes as typed so the daemon refuses it. */
private fun canonicalPath(path: String): String =
    try {
        Path.of(path).toRealPath().toString()
    } catch (e: IOException) {
        path
    } catch (e: InvalidPathException) {
        path
    }

private fun CliEnvironment.registeredProjects(): List<JsonElement> =
    (call(buildJsonObject { put("cmd", "project.list") }) as? JsonArray ?: fail("resposta inesperada do daemon")).toList()

class ProjectGroup : CliktCommand(name = "project") {
    override fun help(context: Context) = "Register and list projects."

    override fun run() = Unit
}

class ProjectAdd(private val cli: CliEnvironment) : CliktCommand(name = "add") {
    private val path by argument(help = "Path of the project repository")

    override fun help(context: Context) = "Register a project (the daemon reads factory.yml from main)."

    override fun run() {
        val canonical = canonicalPath(path)
        val existing = cli.registeredProjects().firstOrNull { (it as? JsonObject)?.let { p -> projectField(p, "repo") } == canonical }
        if (existing != null) {
            cli.out(projectLine(existing))
            return
        }
        val token = cli.ownerToken()
        val request = buildJsonObject {
            put("cmd", "project.add")
            put("actor", "owner")
            put("path", canonical)
            put("proof", token)
        }
        cli.out(projectLine(cli.call(request)))
    }
}

class ProjectList(private val cli: CliEnvironment) : CliktCommand(name = "list") {
    override fun help(context: Context) = "List registered projects."

    override fun run() {
        cli.registeredProjects().forEach { cli.out(projectLine(it)) }
    }
}

fun Sfv2Command.withProjectCommands(cli: CliEnvironment): Sfv2Command =
    subcommands(ProjectGroup().subcommands(ProjectAdd(cli), ProjectList(cli))) as Sfv2Command

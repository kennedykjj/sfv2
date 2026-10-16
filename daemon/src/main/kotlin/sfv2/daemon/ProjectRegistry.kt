package sfv2.daemon

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import sfv2.core.InvalidPolicyException
import sfv2.core.ProjectPolicies

data class ProjectLayer(val name: String, val path: String)

data class Project(val name: String, val repo: String, val layers: List<ProjectLayer>, val policies: ProjectPolicies)

/** The project's `factory.yml` on `main` is invalid; [field] names the offending field. */
class InvalidProjectConfigException(val field: String, message: String) : RuntimeException(message)

class ProjectNotFoundException(message: String) : RuntimeException(message)

class ProjectAlreadyRegisteredException(message: String) : RuntimeException(message)

/** Projects the daemon knows, in memory. A refused registration changes nothing. */
class ProjectRegistry(private val source: ProjectConfigSource) {

    private val projects = LinkedHashMap<Path, Project>()

    @Synchronized
    fun register(repo: Path): Project {
        val canonical = canonicalRepo(repo)
        if (canonical in projects) {
            throw ProjectAlreadyRegisteredException("project already registered: $canonical")
        }
        val text = source.read(canonical)
            ?: throw ProjectNotFoundException("$CONFIG_FILE not found on main in $canonical")
        val project = parse(text, canonical)
        projects[canonical] = project
        return project
    }

    @Synchronized
    fun list(): List<Project> = projects.values.toList()

    private fun canonicalRepo(repo: Path): Path {
        val canonical = try {
            repo.toRealPath()
        } catch (e: IOException) {
            throw ProjectNotFoundException("repository not found: $repo")
        }
        if (!Files.isDirectory(canonical) || !Files.exists(canonical.resolve(".git"))) {
            throw ProjectNotFoundException("not a git repository: $canonical")
        }
        return canonical
    }

    private fun parse(text: String, repo: Path): Project {
        val root = try {
            Load(LoadSettings.builder().setCodePointLimit(MAX_CHARS).setMaxAliasesForCollections(MAX_ALIASES).build())
                .loadFromString(text)
        } catch (e: YamlEngineException) {
            throw InvalidProjectConfigException("factory.yml", "$CONFIG_FILE is not valid YAML")
        }
        val map = asMap(root, "factory.yml")
        rejectUnknown(map, TOP_LEVEL_KEYS, null)
        val projectSection = asMap(map["project"], "project")
        rejectUnknown(projectSection, setOf("name"), "project")
        val name = (projectSection["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: throw InvalidProjectConfigException("project.name", "project.name must be a non-empty text")
        val layers = parseLayers(map["layers"])
        val policies = try {
            ProjectPolicies.from(map.filterKeys { it in POLICY_KEYS })
        } catch (e: InvalidPolicyException) {
            throw InvalidProjectConfigException(e.field, "invalid policy field ${e.field}")
        }
        return Project(name, repo.toString(), layers, policies)
    }

    private fun parseLayers(raw: Any?): List<ProjectLayer> {
        val items = raw as? List<*>
        if (items.isNullOrEmpty()) throw InvalidProjectConfigException("layers", "layers must be a non-empty list")
        val layers = items.mapIndexed { index, item ->
            val field = "layers[$index]"
            val layer = asMap(item, field)
            rejectUnknown(layer, setOf("name", "path"), field)
            val name = (layer["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw InvalidProjectConfigException("$field.name", "$field.name must be a non-empty text")
            val path = (layer["path"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw InvalidProjectConfigException("$field.path", "$field.path must be a non-empty text")
            ProjectLayer(name, path)
        }
        val duplicated = layers.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
        if (duplicated != null) {
            throw InvalidProjectConfigException("layers", "layers has a duplicated name: ${duplicated.key}")
        }
        return layers
    }

    private fun asMap(raw: Any?, field: String): Map<String, Any?> {
        val map = raw as? Map<*, *> ?: throw InvalidProjectConfigException(field, "$field must be a mapping")
        return map.entries.associate { (key, value) ->
            (key as? String ?: throw InvalidProjectConfigException(field, "$field has a key that is not text")) to value
        }
    }

    private fun rejectUnknown(map: Map<String, Any?>, allowed: Set<String>, parent: String?) {
        val unknown = map.keys.firstOrNull { it !in allowed } ?: return
        val field = if (parent == null) unknown else "$parent.$unknown"
        throw InvalidProjectConfigException(field, "unknown field $field")
    }

    private companion object {
        const val CONFIG_FILE = ".shipfloor/factory.yml"
        const val MAX_CHARS = 64 * 1024
        const val MAX_ALIASES = 10
        val POLICY_KEYS = setOf("delivery_mode", "signers", "conventions")
        val TOP_LEVEL_KEYS = POLICY_KEYS + setOf("project", "layers")
    }
}

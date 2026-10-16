package sfv2.daemon

import java.nio.file.Path
import sfv2.core.ProjectPolicies

// shipfloor:qa-stub — only so the red gate compiles; the implementer deletes this file.

interface ProjectConfigSource {
    fun read(repo: Path): String?
}

class GitProjectConfigSource : ProjectConfigSource {
    override fun read(repo: Path): String? = null
}

data class ProjectLayer(val name: String, val path: String)

data class Project(val name: String, val repo: String, val layers: List<ProjectLayer>, val policies: ProjectPolicies)

class InvalidProjectConfigException(val field: String, message: String) : RuntimeException(message)
class ProjectNotFoundException(message: String) : RuntimeException(message)
class ProjectAlreadyRegisteredException(message: String) : RuntimeException(message)

class ProjectRegistry(private val source: ProjectConfigSource) {
    fun register(repo: Path): Project = Project("", repo.toString(), emptyList(), ProjectPolicies.DEFAULT)
    fun list(): List<Project> = emptyList()
}

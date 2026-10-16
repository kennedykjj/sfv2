package sfv2.daemon

/** Loads the instruction skill of a pipeline stage from the daemon's classpath. */
object StageSkills {
    fun load(stage: String): String {
        val path = "skills/$stage/SKILL.md"
        val stream = StageSkills::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("skill do estágio '$stage' não existe no classpath ($path)")
        return stream.use { String(it.readAllBytes(), Charsets.UTF_8) }
    }
}

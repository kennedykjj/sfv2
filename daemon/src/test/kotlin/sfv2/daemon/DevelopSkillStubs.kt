package sfv2.daemon

// shipfloor:qa-stub — lets DevelopSkillTest compile before the implementation exists; the implementer deletes this file.
data class SkillStep(val role: String, val gate: String)

fun StageSkills.steps(stage: String): List<SkillStep> = emptyList()

package sfv2.daemon

/** The prompt of one agent session: the stage skill, then the card and the role this session runs for. */
object StagePrompt {

    fun of(stage: String, cardId: String, role: String? = null): String = buildString {
        append(StageSkills.load(stage).trimEnd())
        append("\n\n## Esta sessão\n\n")
        append("- Card desta sessão: `").append(cardId).append("` (use este id em todas as ferramentas que pedem `cardId`).\n")
        if (role != null) append("- Seu papel nesta sessão: ").append(role).append(".\n")
    }
}

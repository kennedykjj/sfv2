package sfv2.daemon

// shipfloor:qa-stub - lets ConventionTextTest compile before ConventionText exists in main; the implementer deletes it.
object ConventionText {
    fun commitSubject(format: String, type: String, scope: String, subject: String, id: String, ticket: String?): String = ""

    fun pullRequestTitle(format: String, scope: String, subject: String, id: String, ticket: String?): String = ""

    fun pullRequestBody(template: String?, language: String, id: String, ticket: String?): String = ""
}

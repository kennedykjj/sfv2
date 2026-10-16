package sfv2.store

// shipfloor:qa-stub — lets the acceptance tests compile before CardEventStore has the members; delete it
// when assignProject/projectOf exist on CardEventStore.
fun CardEventStore.assignProject(cardId: String, repo: String) {
    // intentionally does nothing
}

fun CardEventStore.projectOf(cardId: String): String? = null

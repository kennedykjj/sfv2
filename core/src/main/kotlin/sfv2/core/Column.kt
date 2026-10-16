package sfv2.core

/** Board columns in board order; [Column.entries] gives the order. */
enum class Column(val displayName: String) {
    INBOX("Inbox"),
    READY_FOR_REFINEMENT("Ready for Refinement"),
    REFINEMENT("Refinement"),
    SPEC_REVIEW("Spec Review"),
    READY_FOR_DEV("Ready for Dev"),
    CODING("Coding"),
    VALIDATION("Validation"),
    STAGED("Staged"),
    READY_FOR_PROD("Ready for Prod"),
    DONE("Done"),
}

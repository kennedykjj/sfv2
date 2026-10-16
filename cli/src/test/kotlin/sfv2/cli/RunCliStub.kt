package sfv2.cli

// shipfloor:qa-stub — lets the acceptance tests compile before runCli takes runCommand; the implementer deletes this file.
fun runCli(
    args: List<String>,
    version: String,
    out: (String) -> Unit,
    err: (String) -> Unit,
    env: Map<String, String>,
    runCommand: (List<String>) -> String?,
): Int = runCli(args, version, out, err, env)

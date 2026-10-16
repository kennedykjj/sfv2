package sfv2.cli

// shipfloor:qa-stub — lets the e2e test compile before runCli has its `env` parameter.
// The implementer deletes this file when adding `env: Map<String, String>` to runCli in Main.kt.
fun runCli(args: List<String>, version: String, out: (String) -> Unit, err: (String) -> Unit, env: Map<String, String>): Int =
    runCli(args, version, out, err)

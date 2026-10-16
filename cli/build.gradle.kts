plugins {
    id("sfv2.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.clikt)
}

application {
    mainClass = "sfv2.cli.MainKt"
    applicationName = "sfv2"
    // Clikt loads a native library (JNA) to read the terminal; without this the JVM warns on every error.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// The CLI reports the version it was built with; the build writes it into a resource.
tasks.processResources {
    // A local value: the filter below must not capture the build script (configuration cache).
    val cliVersion = project.version.toString()
    inputs.property("version", cliVersion)
    filesMatching("sfv2/cli/build.properties") {
        expand("version" to cliVersion)
    }
}

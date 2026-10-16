plugins {
    id("sfv2.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

application {
    mainClass = "sfv2.daemon.MainKt"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":store"))
    implementation(project(":ports"))
    implementation(project(":adapters"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
    implementation(libs.mcp.kotlin.sdk.server)
    implementation(libs.ktor.server.cio)
}

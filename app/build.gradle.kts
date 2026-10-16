plugins {
    id("sfv2.kotlin-jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "sfv2.app.MainKt"
        // The window is opaque and stays on OpenGL: no skiko.renderApi=SOFTWARE (spike S0-07).
    }
}

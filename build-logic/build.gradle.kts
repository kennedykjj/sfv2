plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // Puts the serialization compiler plugin on the classpath: a module applies it with
    // id("org.jetbrains.kotlin.plugin.serialization"), no version (decisions.md #16).
    implementation(libs.kotlin.serialization.plugin)
}

plugins {
    id("sfv2.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

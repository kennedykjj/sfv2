plugins {
    id("sfv2.kotlin-jvm")
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
    implementation(libs.jackson.databind)
}

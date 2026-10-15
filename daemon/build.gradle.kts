plugins {
    id("sfv2.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":store"))
    implementation(project(":ports"))
    implementation(project(":adapters"))
}

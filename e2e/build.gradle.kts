plugins {
    id("sfv2.kotlin-jvm")
}

// Only tests live here: black-box checks of the phase exit criteria, written by QA.
dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":daemon"))
    testImplementation(project(":cli"))
}

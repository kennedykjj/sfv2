plugins {
    id("sfv2.kotlin-jvm")
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqldelight.sqlite.driver)
}

sqldelight {
    databases {
        create("Sfv2Database") {
            packageName.set("sfv2.store.db")
        }
    }
}

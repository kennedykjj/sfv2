pluginManagement {
    includeBuild("build-logic")
}

// Downloads the JDK of the toolchain when the machine has none (a JRE alone cannot compile).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "sfv2"

include("core", "store", "ports", "adapters", "daemon", "cli", "app", "e2e")

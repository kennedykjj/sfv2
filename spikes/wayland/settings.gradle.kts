// Standalone build for the Wayland spike (#7). Not part of the main build: run it with
// ../../gradlew -p spikes/wayland run [-Ptoolkit=wayland]
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wayland-spike"

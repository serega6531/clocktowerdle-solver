pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.10"
        kotlin("multiplatform") version "2.3.10"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "clockwerdle-solver"
include(":web")
include(":extension")
include(":shared")
include(":jvm")

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}

tasks.register<Copy>("copyFrontendToDocs") {
    group = "distribution"
    description = "Build the frontend and copy it into the root docs/ folder for GitHub Pages"
    dependsOn("browserDistribution")
    from(layout.buildDirectory.dir("dist"))
    into(rootProject.layout.projectDirectory.dir("docs"))
}

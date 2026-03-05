plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("src/commonMain/kotlin/generated")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        val commonTest by getting
        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
            }
        }
        val jsMain by getting
        val jsTest by getting
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Ensure `gradlew test` runs the JVM tests for this MPP module.
tasks.register("test") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs JVM tests for the shared module."
    dependsOn("jvmTest")
}

val generateCharacters by tasks.registering {
    group = "codegen"
    description = "Generate Character enum from characters.js for shared module"

    val inputFile = rootProject.file("buildSrc/src/main/resources/characters.js")
    val outputFile = file("src/commonMain/kotlin/generated/characters.kt")

    inputs.file(inputFile)
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()
        CharacterEnumGenerator.generate(inputFile, outputFile)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateCharacters)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    dependsOn(generateCharacters)
}

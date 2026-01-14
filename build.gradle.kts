plugins {
    kotlin("jvm") version "2.2.21"
}

group = "ru.serega6531"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

val generateCharacters by tasks.registering {
    group = "codegen"
    description = "Generate Character enum from characters.js"

    val inputFile = file("buildSrc/src/main/resources/characters.js")
    val outputFile = file("src/main/kotlin/generated/characters.kt")

    inputs.file(inputFile)
    outputs.file(outputFile)

    doLast {
        outputFile.parentFile.mkdirs()
        CharacterEnumGenerator.generate(inputFile, outputFile)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateCharacters)
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin/generated")
        }
    }
}
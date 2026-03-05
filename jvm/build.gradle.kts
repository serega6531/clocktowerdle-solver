plugins {
    kotlin("jvm")
    application
}

group = "ru.serega6531"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("me.tongfei:progressbar:0.10.2")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ru.serega6531.clocktowerdle.JvmMainKt")
    // Silence JDK 21+ restricted native access warning from JLine.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

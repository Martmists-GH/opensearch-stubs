plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("com.google.protobuf") version "0.10.0"
    id("io.ktor.plugin") version "3.5.1"
    id("org.jetbrains.kotlinx.atomicfu") version "0.33.0"
}

group = "com.martmists"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.martmists.opensearch.stubs.MainKt"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")

    for (module in arrayOf(
        "core", "cio",

        "content-negotiation",
        "compression"
    )) {
        implementation("io.ktor:ktor-server-$module:3.5.1")
    }

    for (module in arrayOf(
        "core", "cio",

        "content-negotiation",
    )) {
        implementation("io.ktor:ktor-client-$module:3.5.1")
    }

    implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
    implementation("ch.qos.logback:logback-classic:1.5.35")

    implementation("com.google.protobuf:protobuf-java:4.35.1")
}

repositories {
    mavenCentral()
    google()
}

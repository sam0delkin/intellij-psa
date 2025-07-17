import io.swagger.v3.plugins.gradle.tasks.ResolveTask.Format

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("io.swagger.core.v3.swagger-gradle-plugin") version "2.2.28"
    id("com.github.gmazzo.buildconfig") version "5.5.1"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.swagger.core.v3:swagger-core:2.2.28")
    implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation(project(":"))
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    jvmToolchain(providers.gradleProperty("jvmToolchainVersion").get().toInt())
}

buildConfig {
    buildConfigField("PLUGIN_VERSION", properties("pluginVersion"))
    packageName("com.github.sam0delkin.intellijpsa.util")
}

tasks {
    resolve {
        outputFileName.set("schema")
        outputFormat.set(Format.YAML)
        prettyPrint.set(true)
        classpath.from(sourceSets.main.get().output)
        outputDir.set(file("."))
    }
}

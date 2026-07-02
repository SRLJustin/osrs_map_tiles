import groovy.json.JsonSlurper
import java.net.URL

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta13"
    id("io.freefair.lombok") version "8.13.1"
}

group = "org.explv"
version = "1.0"

repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.runelite.net")
        content {
            includeGroup("net.runelite")
        }
    }
}

var latestRl: String? = null
fun getLatestRunelite(): String {
    if (latestRl != null) {
        return latestRl!!
    }

    val jsonText = URL("https://static.runelite.net/bootstrap.json").readText()
    val json = JsonSlurper().parseText(jsonText) as Map<String, String>
    latestRl = json["version"]
    return latestRl!!
}

dependencies {
    implementation("net.runelite:cache:${getLatestRunelite()}")

    // Directly used by Main.java but only runtime-scoped transitives of net.runelite:cache,
    // so they are absent from the compile classpath. Declared explicitly to fix compilation.
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.antlr:antlr4-runtime:4.13.1")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "org.explv.mapimage.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
}

group = "co.surumene"
version = "2.1.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("co.surumene:wgl-plugin:1.0.1")
    compileOnly("co.surumene:wcl-plugin:0.1.0-SNAPSHOT")
    compileOnly("org.geysermc.geyser:api:2.11.2-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("co.surumene:wgl-plugin:1.0.1")
    testImplementation("co.surumene:wcl-plugin:0.1.0-SNAPSHOT")
    testImplementation("co.surumene:wgl-core:1.0.1")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<ProcessResources>("processResources") {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("WhatAWonderfulChicken")
}

val verifyPluginJar by tasks.registering {
    dependsOn(tasks.named("jar"))

    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val contents = zipTree(jarFile)

        check(contents.matching {
            include("co/surumene/whatawonderfulchicken/WhatAWonderfulChickenPlugin.class")
        }.files.isNotEmpty()) {
            "plugin JAR does not contain the WWC plugin main class"
        }

        check(contents.matching {
            include("co/surumene/wgl/**")
        }.files.isEmpty()) {
            "plugin JAR must not bundle Wonderful Genome Lib classes"
        }

        check(contents.matching { include("co/surumene/wcl/**") }.files.isEmpty()) {
            "consumer JAR must not bundle WonderfulCoreLibrary classes"
        }
        val pluginYml = contents.matching { include("plugin.yml") }.singleFile.readText()
        check(!pluginYml.contains("\${version}")) {
            "plugin.yml version placeholder was not expanded"
        }
        check(pluginYml.contains("WonderfulCoreLibrary")) {
            "plugin.yml must require WonderfulCoreLibrary"
        }
        check(pluginYml.contains("WonderfulGenomeLib")) {
            "plugin.yml must declare WonderfulGenomeLib as a dependency"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPluginJar)
}

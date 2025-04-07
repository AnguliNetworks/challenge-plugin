import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

plugins {
    kotlin("jvm") version "2.1.0"
}

group = "li.angu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "eldonexus"
        url = uri("https://eldonexus.de/repository/maven-releases/")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.77.0")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "name" to project.name,
            "version" to project.version,
            "main" to "li.angu.challengeplugin.ChallengePluginPlugin"
        )
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    // Custom archiveFileName for snapshots
    if (version.toString().contains("SNAPSHOT")) {
        // Generate timestamp in format yyyyMMdd-HHmmss
        val timestamp = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .format(LocalDateTime.now())
            
        // Generate UUID and get first part (before first dash)
        val uuid = UUID.randomUUID().toString().split("-")[0]
        
        // Set custom archive name for snapshots
        archiveFileName.set("${project.name}-v${version.toString().replace("-SNAPSHOT", "")}_SNAPSHOT_${timestamp}_${uuid}.jar")
    }
}

// Task to copy JAR to test server with a consistent dev filename
tasks.register("deployToTestServer") {
    // Depend on build to ensure everything is compiled and tested
    dependsOn("build")
    
    doLast {
        // Create a copy task with a fixed filename
        copy {
            from(tasks.jar.get().archiveFile)
            into("${projectDir}/run/plugins")
            rename { fileName -> "${project.name}-dev.jar" }
        }
        println("Deployed plugin to test server at: ${projectDir}/run/plugins/${project.name}-dev.jar")
    }
}

// Add debug configuration
kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }
    }
}

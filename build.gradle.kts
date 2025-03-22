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
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.77.0")
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
        val timestamp = java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .format(java.time.LocalDateTime.now())
            
        // Generate UUID and get first part (before first dash)
        val uuid = java.util.UUID.randomUUID().toString().split("-")[0]
        
        // Set custom archive name for snapshots
        archiveFileName.set("${project.name}-v${version.toString().replace("-SNAPSHOT", "")}_SNAPSHOT_${timestamp}_${uuid}.jar")
    }
}

// Task to copy JAR to test server
tasks.register<Copy>("deployToTestServer") {
    dependsOn("build")
    from(tasks.jar.get().archiveFile)
    into("${projectDir}/run/plugins")
    doLast {
        println("Deployed plugin to test server at: ${projectDir}/run/plugins")
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

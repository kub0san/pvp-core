import xyz.jpenilla.runpaper.task.RunServer
import java.io.File
import java.nio.charset.StandardCharsets

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    // Multiverse-Core: optional at runtime, used for world lifecycle work.
    maven("https://repo.onarandombox.com/content/groups/public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://nexus.lucko.me/repository/maven-releases/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // Compile against the oldest supported Paper API so the jar stays binary-compatible with
    // every server from 1.21.11 up to 26.3. APIs added after 1.21.11 are reached through
    // reflection; org.pvpcore.util.VersionSupport reports and compares the running version.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // PacketEvents 2.14.0 supports every Minecraft version this plugin targets (1.21.11-26.3).
    // Optional at runtime: Faster Crystals and Shield Fixes need it.
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    // Optional at runtime: only used for the lifecycle side (pregen / reset / world events).
    // The chunk generator itself is pure Bukkit API and works without MV installed.
    compileOnly("org.mvplugins.multiverse.core:multiverse-core:5.8.1")

    // Kits module (merged PerPlayerKit) dependencies.
    implementation("org.ipvp:canvas:1.3.1")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.12.2")
    implementation("redis.clients:jedis:5.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.15")

    // Command framework (shaded). See https://docs.commandapi.dev/dev-setup/shading.html
    implementation("dev.jorel:commandapi-paper-shade:12.1.0")

    // Configurate (SpongePowered) for configuration loading/serialization.
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation("org.spongepowered:configurate-core:4.2.0")

    // Unit tests: MockBukkit v1.21 targets Paper 1.21.11 and emits Java 21 bytecode,
    // matching this plugin's runtime target. Packet/GUI behaviour still needs a real server.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    // Build with the local JDK (25) but emit Java 21 bytecode, because Paper 1.21.11 runs on
    // Java 21. Newer Paper versions run the same jar on Java 25.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// Kits bundles Canvas, OkHttp, Jedis, HikariCP and adventure-platform, so the distributed
// plugin is the shaded jar. Disable the plain jar: it writes the same filename and would
// otherwise replace the shaded jar (dropping bundled dependencies and resources) during build.
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.github.stefvanschie.inventoryframework", "org.pvpcore.inventoryframework")
    relocate("dev.jorel.commandapi", "org.pvpcore.libs.commandapi")
}

tasks.named("build") { dependsOn("shadowJar") }

fun RunServer.configurePvpCore(minecraftVersion: String, runDir: String = "run") {
    this.minecraftVersion(minecraftVersion)
    args("--nogui")
    jvmArgs("-Xms2G", "-Xmx2G")
    // The shared run/ world is in the newest format; older servers cannot open it, which
    // made the compat tasks fail before the plugin was ever loaded. Give each compat line
    // its own isolated server directory instead.
    runDirectory(File(runDir))
    // Plugin jar auto-detection only happens for the default run task; custom tasks must
    // add their plugin jars manually, otherwise the server boots without pvp-core at all.
    pluginJars(tasks.shadowJar)
    // Optional dependency for Faster Crystals and Shield Fixes. The same PacketEvents build
    // supports all target server versions.
    downloadPlugins {
        url("https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar")
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // Emit Java 21 bytecode so the plugin also loads on servers still running Java 21.
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    // Guard against silent source corruption. Windows PowerShell 5.1 writes files as
    // ASCII by default, which turns characters like the section sign into '?' and
    // quietly breaks regexes and message formatting. Any source file that is not
    // valid UTF-8, or that contains the replacement character, fails the build.
    register("checkSourceEncoding") {
        group = "verification"
        description = "Fails the build if any source or resource file is not valid UTF-8."
        val sourceDirs = listOf("src/main/java", "src/main/resources")
        val files = sourceDirs.map { dir ->
            fileTree(dir) { include("**/*.java", "**/*.yml", "**/*.yaml", "**/*.md") }
        }
        inputs.files(files)
        outputs.upToDateWhen { false }
        doLast {
            val utf8 = StandardCharsets.UTF_8
            val problems = mutableListOf<String>()
            files.flatMap { it.files }.forEach { file ->
                val bytes = file.readBytes()
                // Decoding invalid UTF-8 yields U+FFFD, so this single check catches both
                // malformed byte sequences and literal replacement characters already in the file.
                val text = String(bytes, utf8)
                if (text.contains('\uFFFD')) {
                    problems += "${file.path} is not valid UTF-8 or contains the replacement character (U+FFFD)"
                }
            }
            if (problems.isNotEmpty()) {
                problems.forEach { logger.error(it) }
                throw GradleException("Source encoding check failed:\n" + problems.joinToString("\n"))
            }
        }
    }

    test { useJUnitPlatform() }

    named("check") { dependsOn("checkSourceEncoding") }

    // Latest supported version.
    runServer {
        configurePvpCore("26.3")
    }

    // Compatibility smoke tests for the oldest and newest non-26.3 target. Each gets an
    // isolated server directory (a fresh world) because the shared run/ world is in the
    // 26.3 format and older servers cannot open it.
    register<RunServer>("runServer12111") {
        configurePvpCore("1.21.11", "run/1.21.11")
    }
    register<RunServer>("runServer262") {
        configurePvpCore("26.2", "run/26.2")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
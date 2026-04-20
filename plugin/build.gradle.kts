import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "8.3.2"
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":api"))

    compileOnly(libs.paperApi)
    compileOnly(libs.adventureApi)
    implementation(libs.entityLib)
    compileOnly(libs.typeWriter) {
        exclude(group = "io.papermc.paper")
        exclude(group = "com.github.Tofaa2.EntityLib")
        exclude(group = "me.tofaa.entitylib")
    }
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholdersApi)
    compileOnly(libs.floodgateApi)
    compileOnly(libs.geyserApi)
    compileOnly(libs.commonsLang)
    compileOnly(libs.configlib)
    implementation(libs.configlibPaper)
    compileOnly(libs.packeteventsSpigot)
    compileOnly(libs.viaVersionApi)
    compileOnly(libs.bstatsBukkit)
    compileOnly(libs.expiringMap)
    compileOnly(libs.commonsJexl3)
    compileOnly(libs.nexo)
    compileOnly(libs.oraxen)
    compileOnly(libs.itemsAdder)
    compileOnly(libs.creative.rp)
    compileOnly(libs.creative.serializer)
    compileOnly(libs.libs.disguises)
    compileOnly(libs.hmcCosmetics)

    implementation(libs.universalScheduler)
    implementation(libs.libbyBukkit)

    compileOnly(libs.gson)
    compileOnly(libs.feather)
    compileOnly(libs.labymod)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.named<ShadowJar>("shadowJar") {
    val relocation = "org.alexdev.unlimitednametags.libraries."
    relocate("net.byteflux.libby", relocation + "libby.bukkit")
    relocate("org.jetbrains", relocation + "jetbrains")
    relocate("org.intellij", relocation + "intellij")
    relocate("de.themoep", relocation + "themoep")
    relocate("me.tofaa.entitylib", relocation + "entitylib")
    relocate("javax.annotation", relocation + "annotation")
    relocate("com.github.Anon8281.universalScheduler", relocation + "universalScheduler")
    relocate("net.kyori.adventure.text.serializer", "io.github.retrooper.packetevents.adventure.serializer")
    relocate("net.byteflux.libby", relocation + "libby.bukkit")

    relocate("de.exlll.configlib", "org.alexdev.unlimitednametags.configlib")

    dependencies {
        exclude(dependency(":kotlin-stdlib"))
        exclude(dependency(":slf4j-api"))
        exclude(dependency("com.google.code.gson:gson"))
        exclude(dependency("net.kyori:adventure-api"))
        exclude(dependency("net.kyori:adventure-key"))
        exclude(dependency("net.kyori:adventure-nbt"))
        exclude(dependency("net.kyori:option"))
        exclude(dependency("net.kyori:examination-api"))
        exclude(dependency("net.kyori:examination-string"))
        exclude(dependency("net.kyori:text"))
        exclude(dependency("net.kyori:adventure-text-serializer-gson"))
        exclude(dependency("net.kyori:adventure-text-serializer-json"))
    }

    exclude("assets/mappings/block/**")
    exclude("assets/mappings/stats/**")
    exclude("assets/mappings/particle/**")
    exclude("assets/mappings/enchantment/**")

    destinationDirectory.set(file("${rootProject.projectDir}/target"))
    archiveFileName.set("${rootProject.name}.jar")

    minimize()
}

kotlin {
    jvmToolchain(21)
}

tasks.named<Jar>("jar").configure {
    dependsOn("shadowJar")
}
tasks.named<Delete>("clean").configure {
    delete(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
}

tasks.jar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

tasks {
    runServer {
        minecraftVersion("1.21.11")

        downloadPlugins {
            hangar("PlaceholderAPI", "2.11.6")
            modrinth("luckperms", "v5.4.145-bukkit")
            modrinth("multiverse-core", "4.3.14")
            github("MiniPlaceholders", "MiniPlaceholders", "3.0.1", "MiniPlaceholders-Paper-3.0.1.jar")
            url("https://ci.codemc.io/job/retrooper/job/packetevents/796/artifact/build/libs/packetevents-spigot-2.11.1-SNAPSHOT.jar")
            github("MilkBowl", "Vault", "1.7.3", "Vault.jar")
            github("FeatherMC", "feather-server-api", "v0.0.5", "feather-server-api-0.0.5-bukkit.jar")
            github("LabyMod", "labymod4-server-api", "1.0.6", "labymod-server-api-bukkit-1.0.6.jar")
        }
    }
    runPaper.folia.registerTask {
        minecraftVersion("1.21.11")

        downloadPlugins {
            github("Anon8281", "PlaceholderAPI", "2.11.7", "PlaceholderAPI-2.11.7-DEV-Folia.jar")
            url("https://ci.codemc.io/job/retrooper/job/packetevents/796/artifact/build/libs/packetevents-spigot-2.11.1-SNAPSHOT.jar")
            github("ViaVersion", "ViaVersion", "5.4.1", "ViaVersion-5.4.1.jar")
        }
    }
}

tasks.processResources {
    var compiled = true
    if (rootProject.file("license.txt").exists()) {
        compiled = false
    }

    filesMatching("**/UnlimitedNameTags.java") {
        expand(
            "configlibVersion" to libs.versions.configlibVersion.get(),
        )
    }

    from("src/main/resources") {
        include("plugin.yml")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        expand(
            "configlibVersion" to libs.versions.configlibVersion.get(),
            "expiringMapVersion" to libs.versions.expiringMapVersion.get(),
            "commonsJexl3Version" to libs.versions.commonsJexl3Version.get(),

            "version" to version,
            "compiled" to compiled
        )
    }
}

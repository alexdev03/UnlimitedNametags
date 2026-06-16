import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "9.4.2"
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0-RC2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":api-paper"))

    compileOnly(libs.paperApi)
    compileOnly(libs.adventureApi)
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
    compileOnly(libs.configlibPaper)
    compileOnly(libs.entityLib) {
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
        exclude(group = "com.github.retrooper", module = "packetevents-api")
    }
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

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
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
        exclude(dependency("com.github.retrooper:packetevents-spigot"))
        exclude(dependency("com.github.retrooper:packetevents-api"))
        exclude(dependency("org.snakeyaml:snakeyaml-engine"))
        exclude(dependency("org.yaml:snakeyaml"))
        exclude(dependency("com.google.guava:guava"))
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
        exclude(dependency("org.apache.commons:commons-jexl3"))
        exclude(dependency("net.jodah:expiringmap"))
    }

    exclude("assets/mappings/block/**")
    exclude("assets/mappings/stats/**")
    exclude("assets/mappings/particle/**")
    exclude("assets/mappings/enchantment/**")
    exclude("assets/mappings/data/**")
    exclude("assets/mappings/item_base_components/**")

    destinationDirectory.set(file("${rootProject.projectDir}/target"))
    archiveFileName.set("${rootProject.name}.jar")

    minimize()
}

kotlin {
    jvmToolchain(25)
}

tasks.named<Delete>("clean").configure {
    delete(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
}

tasks.jar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks {
    runServer {
        minecraftVersion("26.1.1")

        downloadPlugins {
            hangar("PlaceholderAPI", "2.11.6")
            modrinth("luckperms", "v5.4.145-bukkit")
            modrinth("multiverse-core", "4.3.14")
            modrinth("essentialsx", "2.22.0")
            //url("https://ci.ender.zone/job/EssentialsX/lastSuccessfulBuild/artifact/jars/EssentialsX-2.22.0-dev+112-5baf239.jar")
            github("MiniPlaceholders", "MiniPlaceholders", "3.2.0", "MiniPlaceholders-Paper-3.2.0.jar")
            url("https://github.com/retrooper/packetevents/releases/download/v2.12.1/packetevents-spigot-2.12.1.jar")
            github("MilkBowl", "Vault", "1.7.3", "Vault.jar")
            github("FeatherMC", "feather-server-api", "v0.0.5", "feather-server-api-0.0.5-bukkit.jar")
            github("LabyMod", "labymod4-server-api", "1.0.6", "labymod-server-api-bukkit-1.0.6.jar")
        }
    }
    runPaper.folia.registerTask {
        minecraftVersion("1.21.11")

        downloadPlugins {
            github("Anon8281", "PlaceholderAPI", "2.11.7", "PlaceholderAPI-2.11.7-DEV-Folia.jar")
            url("https://github.com/retrooper/packetevents/releases/download/v2.12.1/packetevents-spigot-2.12.1.jar")
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
            "snakeyamlEngineVersion" to libs.versions.snakeyamlEngineVersion.get(),

            "version" to version,
            "compiled" to compiled
        )
    }
}

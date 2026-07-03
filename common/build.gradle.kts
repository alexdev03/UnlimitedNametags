plugins {
    `java-library`
}

dependencies {
    // implementation: shaded into the plugin JAR (relocated by :paper shadowJar)
    implementation(libs.entityLib) {
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
        exclude(group = "com.github.retrooper", module = "packetevents-api")
    }
    compileOnly(libs.packeteventsSpigot)
    api(libs.adventureApi)
    compileOnly("net.kyori:adventure-text-minimessage:${libs.versions.adventureApiVersion.get()}")
    // implementation so Shadow can minimize ConfigLib + snakeyaml-engine (api deps are kept whole)
    implementation(libs.configlib)
    compileOnlyApi(libs.configlib)
    // Loaded at runtime via plugin.yml libraries (Paper); must not be api or Shadow bundles them whole
    compileOnlyApi(libs.commonsJexl3)
    compileOnlyApi(libs.expiringMap)
    // Compile-only: same SnakeYAML Engine as ConfigLib; loaded at runtime via plugin.yml, not shaded
    compileOnly(libs.snakeyamlEngine)

    compileOnly("com.google.guava:guava:33.3.1-jre")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    `java-library`
}

dependencies {
    api(project(":common"))

    // Types appear in public API signatures; compileOnlyApi publishes them for Maven consumers (like EntityLib)
    compileOnlyApi(libs.entityLib) {
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
        exclude(group = "com.github.retrooper", module = "packetevents-api")
    }
    compileOnlyApi(libs.adventureApi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

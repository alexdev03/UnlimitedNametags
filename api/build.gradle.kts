plugins {
    `java-library`
}

dependencies {
    api(project(":common"))

    // compileOnly: EntityLib is SNAPSHOT — addons declare it themselves; not published on Central POM
    compileOnly(libs.entityLib) {
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
        exclude(group = "com.github.retrooper", module = "packetevents-api")
    }
    compileOnlyApi(libs.adventureApi)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

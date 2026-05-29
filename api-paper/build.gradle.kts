plugins {
    `java-library`
}

dependencies {
    api(project(":api"))
    compileOnly(libs.paperApi)
    compileOnlyApi(libs.entityLib) {
        exclude(group = "com.github.retrooper", module = "packetevents-spigot")
        exclude(group = "com.github.retrooper", module = "packetevents-api")
    }
    compileOnlyApi(libs.adventureApi)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

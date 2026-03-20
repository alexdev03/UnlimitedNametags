plugins {
    `java-library`
}

dependencies {
    api(project(":common"))
    compileOnly(libs.paperApi)
    compileOnly(libs.adventureApi)
    compileOnly(libs.packeteventsSpigot)
    compileOnly(libs.configlib)
    compileOnly(libs.entityLib)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

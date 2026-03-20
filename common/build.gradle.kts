plugins {
    `java-library`
}

dependencies {
    compileOnly("com.google.guava:guava:33.3.1-jre")
    compileOnly(libs.packeteventsSpigot)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly("org.jetbrains:annotations:26.0.2")
}

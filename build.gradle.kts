import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

allprojects {
    group = "org.alexdev"
    version = property("version") as String
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.nexomc.com/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://repo.extendedclip.com/releases")
        maven("https://repo.minebench.de/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://libraries.minecraft.net/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.viaversion.com/")
        maven("https://maven.pvphub.me/#/tofaa/io/github/tofaa2/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.oraxen.com/releases")
        maven("https://repo.oraxen.com/snapshots")
        maven("https://maven.pvphub.me/tofaa")
        maven("https://jitpack.io")
        maven("https://repo.alessiodp.com/snapshots/")
        maven("https://maven.typewritermc.com/beta")
        maven("https://repo.nexomc.com/releases")
        maven("https://repo.md-5.net/content/groups/public/")
        maven("https://mvn.lib.co.nz/public")
        maven {
            name = "feather-repo"
            url = uri("https://repo.feathermc.net/artifactory/maven-releases")
        }
        maven {
            name = "labymod"
            url = uri("https://dist.labymod.net/api/v1/maven/release/")
        }
        maven("https://repo.hibiscusmc.com/releases")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }
    extensions.configure<JavaPluginExtension>("java") {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        disableAutoTargetJvm()
    }
}

val publishableLibraryModules = mapOf(
    ":common" to "unlimitednametags-common",
    ":api" to "unlimitednametags-api",
    ":api-paper" to "unlimitednametags-api-paper",
)

configure(publishableLibraryModules.keys.map { project(it) }) {
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension>("java") {
        withJavadocJar()
        withSourcesJar()
    }

    tasks.named<Javadoc>("javadoc") {
        isFailOnError = false
    }

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = rootProject.group.toString()
                artifactId = publishableLibraryModules[path]!!
            }
        }
        repositories {
            mavenLocal()
            val centralUrl = findProperty("mavenCentralUrl") as String?
            val centralUsername = findProperty("mavenCentralUsername") as String?
                ?: System.getenv("MAVEN_CENTRAL_USERNAME")
            val centralPassword = findProperty("mavenCentralPassword") as String?
                ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
            if (centralUrl != null && centralUsername != null && centralPassword != null) {
                maven {
                    name = "mavenCentral"
                    url = uri(centralUrl)
                    credentials {
                        username = centralUsername
                        password = centralPassword
                    }
                }
            }
        }
    }
}

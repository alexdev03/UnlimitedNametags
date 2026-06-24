import groovy.util.Node
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

allprojects {
    group = "io.github.alexdev03"
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
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        disableAutoTargetJvm()
    }
}

val publishableLibraryModules = mapOf(
    ":common" to "unlimitednametags-common",
    ":api" to "unlimitednametags-api",
    ":api-paper" to "unlimitednametags-api-paper",
)

val publishableLibraryDescriptions = mapOf(
    ":common" to "Shared config, JEXL, animations, and platform bridges for UnlimitedNametags",
    ":api" to "UUID and Adventure service interfaces for UnlimitedNametags (no Paper)",
    ":api-paper" to "Paper/Bukkit API for UnlimitedNametags addons (UNTPaperAPI, Formatter, Player overloads)",
)

configure(publishableLibraryModules.keys.map { project(it) }) {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

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
                pom {
                    name.set(publishableLibraryModules[path]!!)
                    description.set(publishableLibraryDescriptions[path]!!)
                    url.set("https://github.com/alexdev03/UnlimitedNametags")
                    licenses {
                        license {
                            name.set("GNU General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                        }
                    }
                    developers {
                        developer {
                            id.set("alexdev03")
                            name.set("Alex")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/alexdev03/UnlimitedNametags.git")
                        developerConnection.set("scm:git:ssh://github.com:alexdev03/UnlimitedNametags.git")
                        url.set("https://github.com/alexdev03/UnlimitedNametags")
                    }
                    withXml {
                        @Suppress("UNCHECKED_CAST")
                        val dependencies = (asNode().get("dependencies") as? groovy.util.NodeList)
                            ?.firstOrNull() as? Node ?: return@withXml
                        dependencies.children().toList().forEach { dependency ->
                            if (dependency !is Node) return@forEach
                            @Suppress("UNCHECKED_CAST")
                            val version = (dependency.get("version") as? groovy.util.NodeList)
                                ?.firstOrNull() as? Node
                            if (version?.text()?.contains("SNAPSHOT") == true) {
                                dependencies.remove(dependency)
                            }
                        }
                    }
                }
            }
        }
        repositories {
            mavenLocal()
            val projectVersion = version.toString()
            val centralUrl = findProperty("mavenCentralUrl") as String?
                ?: if (projectVersion.endsWith("SNAPSHOT")) {
                    "https://central.sonatype.com/repository/maven-snapshots/"
                } else {
                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                }
            val centralUsername = findProperty("mavenCentralUsername") as String?
                ?: System.getenv("MAVEN_CENTRAL_USERNAME")
            val centralPassword = findProperty("mavenCentralPassword") as String?
                ?: System.getenv("MAVEN_CENTRAL_PASSWORD")
            if (centralUsername != null && centralPassword != null) {
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

    extensions.configure<SigningExtension> {
        val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
        val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        } else {
            useGpgCmd()
        }
        sign(extensions.getByType<PublishingExtension>().publications.getByName("maven"))
    }
}

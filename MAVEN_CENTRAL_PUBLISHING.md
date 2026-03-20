# Maven Central (multi-module)

Artifacts (coordinates follow `group` from root `build.gradle.kts`, `version` from `gradle.properties`):

| Module   | Artifact ID            | Typical use                          |
|----------|------------------------|--------------------------------------|
| `common` | `unlimitednametags-common` | Internal shared types (optional)   |
| `api`    | `unlimitednametags-api`    | `compileOnly` for plugin integrations |
| `plugin` | `unlimitednametags`        | Server plugin (fat jar not on Central; publish thin `jar` only if needed) |

## Publishing with `maven-publish`

Apply `maven-publish` to **`common`** and **`api`** (`java-library` + `sourcesJar` + `javadocJar` as needed). The **`plugin`** module is usually installed locally or uploaded as a release asset; the **shadow** JAR is produced by `:plugin:shadowJar` into `target/UnlimitedNametags.jar`.

Example install:

```bash
./gradlew :common:publishToMavenLocal :api:publishToMavenLocal
```

Integrators depend on the **api** artifact for `UNTAPI`, `Settings`, and service interfaces; they must **not** bundle the implementation-only classes from `plugin`.

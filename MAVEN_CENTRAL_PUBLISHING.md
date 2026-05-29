# Maven Central (multi-module)

Artifacts (coordinates follow `group` from root `build.gradle.kts`, `version` from `gradle.properties`):

| Module        | Artifact ID                 | Typical use |
|---------------|-----------------------------|-------------|
| `common`      | `unlimitednametags-common`  | Shared config, JEXL, animations, platform bridges (optional) |
| `api`         | `unlimitednametags-api`     | UUID + Adventure service interfaces (no Paper) |
| `api-paper`   | `unlimitednametags-api-paper` | Paper/`Player` API: `UNTPaperAPI`, `Formatter`, `*Paper` defaults |
| `paper`       | `unlimitednametags`         | Server plugin (fat jar not on Central; publish thin `jar` only if needed) |

## Publishing with `maven-publish`

Apply `maven-publish` to **`common`**, **`api`**, and **`api-paper`** (`java-library` + `sourcesJar` + `javadocJar` as needed). The **`paper`** module is usually installed locally or uploaded as a release asset; the **shadow** JAR is produced by `:paper:shadowJar` into `target/UnlimitedNametags.jar`.

Example install:

```bash
./gradlew :common:publishToMavenLocal :api:publishToMavenLocal :api-paper:publishToMavenLocal
```

- **Addon integrations** (`UNTPaperAPI`, `Player` overloads): `compileOnly` **`api-paper`** (transitively exposes **`api`** + **`common`**).
- **Headless / UUID-only** integrations: **`api`** or **`common`** only.
- Do **not** bundle classes from **`plugin`**.

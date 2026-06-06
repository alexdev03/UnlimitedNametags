<div align="center">

# UnlimitedNameTags

Custom stacked nametags for Paper servers: text, item, and block display rows with placeholders, animations, visibility rules, and a developer API.

[![GitHub Release](https://img.shields.io/github/release/alexdev03/unlimitednametags.svg)](https://github.com/alexdev03/UnlimitedNameTags/releases)
[![CodeFactor](https://www.codefactor.io/repository/github/alexdev03/unlimitednametags/badge)](https://www.codefactor.io/repository/github/alexdev03/unlimitednametags)
[![BuiltByBit](https://img.shields.io/badge/BuiltByBit-resource-lightblue?style=for-the-badge)](https://builtbybit.com/resources/unlimitednametags.46172/)
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/W4Fu8fqCKs)

<img alt="UnlimitedNameTags in action" src="https://i.imgur.com/w7zlGaO.gif" width="640">

</div>

## Requirements

- Paper 1.21.4+ or a compatible Paper fork such as Purpur or Folia.
- PacketEvents installed on the server.
- Spigot and other non-Paper servers are not supported.

## Features

- Multiple stacked `displayGroups` per player.
- TEXT rows with structured lines and optional per-line `when` conditions.
- ITEM and BLOCK rows with configurable material, scale, offset, billboard, glow, and animations.
- PlaceholderAPI, MiniPlaceholders, vanish integrations, Geyser support notes, and hook support for common cosmetic plugins.
- Config migration through `configVersion` and `SettingsYamlMigrator`.

## Install

1. Put PacketEvents and `UnlimitedNametags.jar` in `plugins/`.
2. Restart the server.
3. Edit `plugins/UnlimitedNameTags/settings.yml`.

## Build

```bash
./gradlew :paper:shadowJar
```

On Windows:

```bat
gradlew.bat :paper:shadowJar
```

The server jar is written to `target/UnlimitedNametags.jar`.

## API

Use the artifact that matches the API surface you need:

```kotlin
dependencies {
    compileOnly("io.github.alexdev03:unlimitednametags-api-paper:2.0.0")
    // compileOnly("io.github.alexdev03:unlimitednametags-api:2.0.0")
}
```

- `api-paper`: Paper/Bukkit types, `UNTPaperAPI`, `Player` overloads, `Formatter`.
- `api`: UUID and Adventure-only interfaces.
- `common`: shared config and value types used by the API.

Publishing notes are in [MAVEN_CENTRAL_PUBLISHING.md](MAVEN_CENTRAL_PUBLISHING.md). Breaking changes are tracked in [CHANGELOG.md](CHANGELOG.md).

## Minimal Config Example

```yaml
displayGroups:
  - lines:
      - text: "%luckperms_prefix%%player_name%"
      - text: "&a%player_ping%ms"
        when: "%player_ping% < 70"
    scale: 1.0
    yOffset: 1.0
```

## Support

Use [Discord](https://discord.gg/W4Fu8fqCKs) for questions and support.

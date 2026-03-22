<div align="center">

# **UnlimitedNameTags**

*Custom stacked name tags — text, items, and blocks — with placeholders, animations, and a clean API.*

<br>

[![GitHub Release](https://img.shields.io/github/release/alexdev03/unlimitednametags.svg)](https://github.com/alexdev03/UnlimitedNameTags/releases)
[![CodeFactor](https://www.codefactor.io/repository/github/alexdev03/unlimitednametags/badge)](https://www.codefactor.io/repository/github/alexdev03/unlimitednametags)

<br>

[![SpigotMC](https://img.shields.io/badge/SpigotMC-117526-blue?style=for-the-badge)](https://www.spigotmc.org/resources/unlimitednametags.117526/)
[![BuiltByBit](https://img.shields.io/badge/BuiltByBit-resource-lightblue?style=for-the-badge)](https://builtbybit.com/resources/unlimitednametags.46172/)
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/W4Fu8fqCKs)

<br>

<img alt="Unlimited Name Tags in action" src="https://i.imgur.com/w7zlGaO.gif" width="640">

</div>

---

## Contents

| | |
|:---|:---|
| [Overview](#overview) | What it is and what you need |
| [Features](#features) | Behaviour and config highlights |
| [Build & API](#build--api) | Gradle modules and developer jar |
| [Getting started](#getting-started) | Install, deps, first config |
| [Commands](#commands) | Slash commands |
| [Permissions](#permissions) | Defaults |
| [Integrations](#integrations) | Plugins and platforms |
| [Supported versions](#supported-versions) | Server & client notes |
| [Support](#support) | Discord |

---

## Overview

**UnlimitedNameTags** is a **Paper-first** plugin (**1.20.1+**; Spigot **1.20.2+** also supported) that replaces vanilla name tags with **display entities** mounted on the player. Tags move smoothly with the player (client-side interpolation, not per-tick teleports).

Each player can have several **stacked rows** (`displayGroups`): **TEXT**, **ITEM**, or **BLOCK**, each with its own scale, vertical offset, optional billboard, optional **`when`** (JEXL + PlaceholderAPI), and optional **animation**.

> **Upgrading to 2.x?** See **[CHANGELOG.md](CHANGELOG.md)** for breaking changes (`displayGroups`, removal of `modifiers`, API return types, etc.).

---

## Features

### Core

| | |
|:---|:---|
| **Placeholders** | [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI); relational placeholders optional |
| **Vanish** | Hides tags for vanished players when your vanish plugin integrates |
| **Formatters** | MiniMessage, MineDown, Legacy, or Universal (see config comments) |
| **Own nametag** | Optional “see your own tag” (Lunar-style) |
| **Sneak** | Configurable text opacity while crouching |

### Display rows (`displayGroups`)

| | |
|:---|:---|
| **TEXT** | Multi-line Adventure components, per-row background / shadow / see-through |
| **ITEM** | `itemMaterial`, `itemDisplayMode`; **`lines` not used** — set material only |
| **BLOCK** | `blockMaterial`; same as item — no text lines |
| **Billboard** | Per-row **`billboard`** or global **`defaultBillboard`** (`CENTER`, `HORIZONTAL`, `VERTICAL`, `FIXED`) |
| **Animations** | `rotate`, `bob`, `dvd_bounce`, `pulse_scale`, `wiggle`, `orbit`, plus **`custom`** via API; **`animationInterval`** / **`displayAnimationInterval`** / **`taskInterval`** control tick rate |
| **Visibility** | Single **`when:`** per row (no `modifiers` in 2.x) |

### Config quality-of-life

| | |
|:---|:---|
| **Optional `background`** | Omit the key for a transparent default; redundant “disabled integer RGB 0” blocks are normalized away |
| **Through-wall hint** *(optional)* | **`obscuredNametagThroughWalls`**: if a viewer has **no clear line of sight** to the player but is within range, the **text** row can appear **dimmer** so names behind walls are still noticeable. Tune **`obscuredNametagOpacity`**, **`obscuredNametagMaxDistance`**, **`obscuredNametagCheckInterval`**. *(TEXT only; sync task — uses `hasLineOfSight`.)* |
| **Migration** | `configVersion` + **`SettingsYamlMigrator`** (backup, v1 → v2, YAML cleanup) |

### Bedrock

Limited support via **Geyser** (text displays may fall back to armor stands on the client).

---

## Build & API

| Module | Role |
|:---|:---|
| **`common`** | Shared internals |
| **`api`** | `UNTAPI`, `Settings`, `UntNametagDisplay`, animations — depend on this for integrations |
| **`plugin`** | Runnable plugin + shadow jar |

**Build the server jar**

```bash
./gradlew :plugin:shadowJar
```

On Windows: `gradlew.bat :plugin:shadowJar`

**Output:** `target/UnlimitedNametags.jar`

Maven Central / publishing: **[MAVEN_CENTRAL_PUBLISHING.md](MAVEN_CENTRAL_PUBLISHING.md)**.

---

## Getting started

1. Install **[PacketEvents](https://modrinth.com/plugin/packetevents)** and **UnlimitedNameTags** in `plugins/`.
2. Restart the server.
3. Edit **`plugins/UnlimitedNameTags/settings.yml`** (generated on first run).

### Example — item row + animation

```yaml
displayGroups:
  - displayType: ITEM
    itemMaterial: DIAMOND
    scale: 1.0
    yOffset: 0.2
    animationInterval: 2
    animation:
      type: rotate
      axis: Y
      degreesPerSecond: 120
      speed: 1.0
      customProperties:
        example_key: example_value
```

### Example — optional through-wall dimming (TEXT)

```yaml
obscuredNametagThroughWalls: true
obscuredNametagOpacity: 55
obscuredNametagMaxDistance: 48
obscuredNametagCheckInterval: 5
```

### Custom animations (API)

- YAML: `animation.type: custom` + `id: your_key`
- Register: `UNTAPI.registerNametagCustomAnimation(...)` and drive pose with **`NametagAnimationTarget`**.

### Optional `advanced.yml`

Copy **`advanced.example.yml`** → **`plugins/UnlimitedNameTags/advanced.yml`** for helmet-height rules (e.g. ItemsAdder / CMD). **`/unt reload`** reloads it when the file exists.

---

## Commands

| Command | Permission | Description |
|:---|:---|:---|
| `/unt` | — | Version and help |
| `/unt reload` | `unt.reload` | Reload configs |
| `/unt debug` | `unt.debug` | Debug snapshot |
| `/unt debugger <true/false>` | `unt.debug` | Toggle debug logging |
| `/unt show <player>` | `unt.show` | Show that player’s nametag |
| `/unt hide <player>` | `unt.hide` | Hide that player’s nametag |
| `/unt refresh <player>` | `unt.refresh` | Re-apply nametag for all viewers |
| `/unt billboard <type>` | `unt.billboard` | Set **default** billboard |
| `/unt formatter <formatter>` | `unt.formatter` | Set text formatter |
| `/unt hideOtherNametags [-h]` | `unt.hideOtherNametags` | Hide others’ tags for you |
| `/unt showOtherNametags [-h]` | `unt.showOtherNametags` | Show others’ tags again |

---

## Permissions

| Permission | Default | Effect |
|:---|:---:|:---|
| `unt.shownametags` | **true** | See other players’ nametags |
| `unt.showownnametag` | **true** | See your own nametag (when enabled in config) |

---

## Integrations

- **PlaceholderAPI** — dynamic and relational placeholders  
- **Vanish plugins** — hide tags when appropriate  
- **TypeWriter** — cinematic / cutscene hooks  
- **Nexo / Oraxen** — helmet / model compatibility  
- **MiniPlaceholders** — when using MiniMessage  
- **Custom plugins** — via **`api`** artifact and **`UNTAPI`**

---

## Supported versions

| Platform | Notes |
|:---|:---|
| **Paper** | **1.20.1+** — **recommended** |
| **Spigot** | **1.20.2+** — supported; not all builds tested |

**Clients:** **ViaBackwards** users may not see display-based nametags correctly. Prefer matching or close server/client versions. *(Some setups use ViaVersion + ViaBackwards with a supported server — YMMV.)*

---

## Support

Questions or issues: **[Discord](https://discord.gg/W4Fu8fqCKs)** — use **#chat** for general talk; open a ticket for licensed support where applicable.

---

<div align="center">

**UnlimitedNameTags** — stacked, animated, API-friendly nametags.

</div>

# Changelog

## 2.0.0

### Breaking changes

- **`DisplayGroup.modifiers` removed** (2.0.x): Row visibility is **only** **`when`** (JEXL, PAPI-expanded). Replace lists like **`global` / `conditional` / `when`** modifiers with a single **`when:`** on the group. **`UntConditionalManager.evaluateExpression(Settings.ConditionalModifier)`** removed — use **`evaluateCondition(String)`**.
- **`lines` for ITEM/BLOCK**: Ignored at runtime (always empty internally). Use **`itemMaterial`** / **`blockMaterial`** only; omit **`lines`** in YAML. On load, **`SettingsYamlMigrator`** strips **`modifiers`** and drops **`lines`** on ITEM/BLOCK groups.
- Merged [PR #61](https://github.com/alexdev03/UnlimitedNametags/pull/61): one text-display entity per **lines group** (per-group background, scale, `yOffset`).
- **Config**: `NameTag` no longer has a single `background` / `scale`; each **`DisplayGroup`** carries `background`, `scale`, and `yOffset`. YAML list key is **`displayGroups`**. Update `settings.yml` accordingly; **`SettingsYamlMigrator`** renames the obsolete dev-only key `linesGroups` → `displayGroups` on load (same **`configVersion` 2**).
- **`UNTAPI.getPacketDisplayText(Player)`** returns `Collection<? extends UntNametagDisplay>` (was `Optional<PacketNameTag>` before 1.x multi-entity work).
- **Gradle**: multi-module layout — `common`, `api`, `plugin`. The runnable jar is `:plugin:shadowJar` → `target/UnlimitedNametags.jar`.
- **`UNTAPI`** and **`Settings`** types use **`UnlimitedNameTagsPlugin`** where applicable; **`Formatter`** / **`TriFunction`** use the same type.
- **Forced nametag** API applies only to the **first** line-group display (avoids duplicating text on every stacked entity).

### Fixes (vs. raw PR branch)

- Stable **ordering** of displays per player (`CopyOnWriteArrayList` per UUID) so line groups map 1:1 to entities.
- **`setNametagSeeThrough`** applies override correctly.
- **`NameTag.withDisplayGroups(UnaryOperator)`** uses a materialized list (no lazy Guava transform).
- **Passenger packet** ordering: vanilla passengers sorted, then nametag entity IDs in line order.
- **`sendPassengersPacket`** guard when the display is removed.
- **Display animations not visible on clients**: Text/item/block display meta uses **`notifyAboutChanges(false)`** (EntityLib), so translation/rotation/scale from **`applyDisplayTransform`** (including the async animation tick) did not generate entity metadata packets. **`PacketNameTag`** now calls **`refresh()`** after applying the transform so viewers see motion.
- **`/unt reload`** applies **per-row billboard** from each **`DisplayGroup`** (optional **`billboard`**) instead of forcing the global default on every entity.

### Added

- **Through-wall nametag dimming** (optional): `obscuredNametagThroughWalls` uses the server’s line-of-sight check (`Player#hasLineOfSight`) on a configurable interval on the **main/region thread** (required for a correct ray trace). Viewers without clear sight to the nametag owner (within `obscuredNametagMaxDistance`) get `obscuredNametagOpacity` and forced text-display `seeThrough` so the tag can read through geometry. Tuning: `obscuredNametagCheckInterval` (ticks), `obscuredNametagMaxDistance`, `obscuredNametagOpacity` (same byte range as `sneakOpacity`).
- **Cleaner YAML**: ConfigLib `outputNulls(false)` — optional fields are omitted instead of `key: null` (same for `advanced.yml`). Migrator dumps without null map entries.
- **YAML robustness**: `placeholdersReplacements` entries are normalized before ConfigLib loads: YAML 1.1 parses unquoted `Yes`/`No` as booleans — they are rewritten as strings (`true`→`Yes`, `false`→`No`) to match `PlaceholderReplacement`. Prefer quoted keys in YAML, e.g. `placeholder: "Yes"`.
- **`settings.yml` versioning**: root `configVersion` (see `SettingsConfigVersion.CURRENT`, **2** = structured nametags with **`displayGroups`**). On load/reload the plugin runs **`SettingsYamlMigrator`**: backs up to `settings.yml.backup-<epoch>.yml`, migrates **v1** (legacy flat `lines` / `background` / `scale`) → **`displayGroups`**, normalizes obsolete YAML key **`linesGroups`** → **`displayGroups`** when present, then rewrites the file if needed. Files without `configVersion` use legacy detection (flat `lines` on a tag).
- **`DisplayGroup.background` optional** in YAML: omit the key for a disabled transparent default (handy for item/block-only rows). Code uses `effectiveBackground()` when reading.
- **Redundant default `background`**: A disabled **`type: integer`** block with RGB `0`, `opacity: 0`, and `shadowed: false` (any `seeThrough`) is treated like a missing key — `DisplayGroup`’s compact constructor clears it to `null`, and **`SettingsYamlMigrator`** removes that YAML block on load so the file stays minimal. The fallback **`effectiveBackground()`** for omitted rows now uses **`seeThrough: true`** (was `false`), matching typical “transparent text” configs.
- **Implementation**: `PacketNameTag` is an abstract base class with package-private `TextPacketNameTag`, `ItemPacketNameTag`, and `BlockPacketNameTag`; use **`PacketNameTag.create(plugin, player, displayGroup)`** (not `new PacketNameTag(...)`) when instantiating from outside the packet package.
- **Item display** and **block display** per display group: set `displayType` to `ITEM` or `BLOCK` on a **`DisplayGroup`**, with optional `itemMaterial`, `blockMaterial`, and `itemDisplayMode` (e.g. `HEAD`, `GROUND`, `FIXED`). Material strings support PlaceholderAPI; if `itemMaterial` / `blockMaterial` is omitted, defaults to **`STONE`**. When **`when`** hides the group, item/block content is cleared like empty text.
- **API**: **`UNTAPI.setNametagDisplayGroups`** replaces **`setNametagLines`** (deprecated, removal in a future release).
- **`UnlimitedNameTagsPlugin`**, **`UntNametagManager`**, **`UntVanishManager`**, **`UntConditionalManager`**, **`UntNametagDisplay`** for compile-only integration against the `api` artifact.
- **`formatTextForNametag`** on the plugin interface (MiniPlaceholders when present).
- **Row conditions**: optional **`when`** on each **`displayGroups`** entry (single JEXL string; PAPI-expanded).
- **Per-`displayGroups` entry animations** (optional `animation`): **`rotate`** (axis `Y` / `X` / `Z` / `XYZ`), **`bob`** (vertical sine), **`dvd_bounce`** (2D box bounce in local XZ), **`pulse_scale`**, **`wiggle`**, **`orbit`**. Each type has tunable fields plus shared **`enabled`**, **`speed`**, and optional **`customProperties`** (`Map<String, String>` for add-ons). Applies to text, item, and block display rows.
- **Animation tick interval**: root **`displayAnimationInterval`** (ticks between pose updates; default **1**). Set to **0** to use **`taskInterval`** (same cadence as placeholder refresh). Per row, optional **`animationInterval`** overrides the global value.
- **Custom animations (API)**: YAML **`animation.type: custom`** + **`id`**, or **`NametagDisplayAnimations.custom(id)`** from code. Register **`NametagCustomAnimationHandler`** with **`UNTAPI.registerNametagCustomAnimation`** / **`UnlimitedNameTagsPlugin`**. Pose via **`NametagAnimationTarget`** (implemented by packet displays; use **`instanceof`** on **`UntNametagDisplay`**).
- **`DisplayGroup.lines`**: only for **TEXT** rows. **`NametagDisplayAnimations`** + **`UNTAPI.setNametagDisplayGroupAnimation`** / **`clearNametagDisplayGroupAnimation`** for programmatic animation changes.
- **Optional `billboard` per `DisplayGroup`**: **`CENTER`**, **`HORIZONTAL`**, **`VERTICAL`**, **`FIXED`** — overrides root **`defaultBillboard`** for that stacked row only. **`DisplayGroup#effectiveBillboard(Settings)`** resolves the constraint.

### Config migration (conditions)

**Before (`modifiers`):**

```yaml
modifiers:
  - type: conditional
    parameter: "%vault_eco_balance%"
    condition: ">"
    value: "1000"
```

**After (group-level `when` only):**

```yaml
when: "%vault_eco_balance% > 1000"
```

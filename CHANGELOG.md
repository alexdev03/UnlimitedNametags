# Changelog

## 2.0.0

### Breaking changes

- Merged [PR #61](https://github.com/alexdev03/UnlimitedNametags/pull/61): one text-display entity per **lines group** (per-group background, scale, `yOffset`).
- **Config**: `NameTag` no longer has a single `background` / `scale`; each `LinesGroup` carries `background`, `scale`, and `yOffset`. Update `config.yml` accordingly.
- **`UNTAPI.getPacketDisplayText(Player)`** returns `Collection<? extends UntNametagDisplay>` (was `Optional<PacketNameTag>` before 1.x multi-entity work).
- **Gradle**: multi-module layout — `common`, `api`, `plugin`. The runnable jar is `:plugin:shadowJar` → `target/UnlimitedNametags.jar`.
- **`UNTAPI`** and **`Settings`** modifiers use **`UnlimitedNameTagsPlugin`**; **`Formatter`** / **`TriFunction`** use the same type.
- **Forced nametag** API applies only to the **first** line-group display (avoids duplicating text on every stacked entity).

### Fixes (vs. raw PR branch)

- Stable **ordering** of displays per player (`CopyOnWriteArrayList` per UUID) so line groups map 1:1 to entities.
- **`setNametagSeeThrough`** applies override correctly.
- **`NameTag.withLinesGroups(UnaryOperator)`** uses a materialized list (no lazy Guava transform).
- **Passenger packet** ordering: vanilla passengers sorted, then nametag entity IDs in line order.
- **`sendPassengersPacket`** guard when the display is removed.

### Added

- **`UnlimitedNameTagsPlugin`**, **`UntNametagManager`**, **`UntVanishManager`**, **`UntConditionalManager`**, **`UntNametagDisplay`** for compile-only integration against the `api` artifact.
- **`formatTextForNametag`** on the plugin interface (MiniPlaceholders when present).

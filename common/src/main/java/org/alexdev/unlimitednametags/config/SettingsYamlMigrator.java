package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.FlowStyle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Objects;

/**
 * Migrates {@code settings.yml} on disk before ConfigLib loads it. Keeps {@code configVersion} in sync with
 * {@link SettingsConfigVersion#CURRENT}.
 */
public final class SettingsYamlMigrator {

    /**
     * Subfolder of the plugin data folder where timestamped copies of {@code settings.yml} are stored before migration.
     */
    private static final String MIGRATION_BACKUP_DIR = "migration-backups";

    private SettingsYamlMigrator() {
    }

    /**
     * If the file exists and is older than the current schema, rewrites it (after a timestamped backup).
     */
    public static void migrateIfNeeded(@NotNull Path settingsPath, @NotNull Logger log) throws IOException {
        if (!Files.isRegularFile(settingsPath)) {
            return;
        }

        final Map<String, Object> root = loadYamlMap(settingsPath);
        if (root == null) {
            log.warning("settings.yml is empty or not a map; skipping migration.");
            return;
        }

        int declared = parseDeclaredVersion(root);
        boolean changed = false;
        final boolean legacy = hasLegacyFlatNameTags(root);
        final boolean stringLineDisplayGroups = hasStringLineDisplayGroups(root);
        final int from;
        if (declared == SettingsConfigVersion.CURRENT && stringLineDisplayGroups) {
            log.info("settings.yml has configVersion " + SettingsConfigVersion.CURRENT
                    + " but still uses v2 string lines; migrating lines to structured rows.");
            from = SettingsConfigVersion.STRING_LINE_DISPLAY_GROUPS;
        } else if (declared > 0) {
            from = declared;
        } else if (legacy) {
            from = SettingsConfigVersion.LEGACY_FLAT_NAMETAG;
        } else if (stringLineDisplayGroups) {
            from = SettingsConfigVersion.STRING_LINE_DISPLAY_GROUPS;
        } else {
            from = SettingsConfigVersion.CURRENT;
        }

        if (from > SettingsConfigVersion.CURRENT) {
            log.warning("settings.yml configVersion " + from + " is newer than this plugin supports ("
                    + SettingsConfigVersion.CURRENT + "); not modifying the file.");
            return;
        }

        changed |= renameObsoleteLinesGroupsKey(root, log);
        changed |= sanitizePlaceholdersReplacements(root, log);
        for (int v = from; v < SettingsConfigVersion.CURRENT; v++) {
            switch (v) {
                case 1 -> changed |= migrateV1ToV2(root, log);
                case 2 -> changed |= migrateV2ToV3(root, log);
                case 3 -> changed |= migrateV3ToV4(root, log);
                case 4 -> changed |= migrateV4ToV5(root, log);
                case 5 -> changed |= migrateV5ToV6(root, log);
                case 6 -> changed |= migrateV6ToV7(root, log);
                default -> throw new IllegalStateException("Missing settings migrator from v" + v + " to v" + (v + 1));
            }
        }
        changed |= stripObsoleteDisplayGroupFields(root, log);
        changed |= stripRedundantDisplayBackgrounds(root, log);

        final Object currentDeclared = root.get("configVersion");
        final int currentInFile = currentDeclared instanceof Number n ? n.intValue() : 0;
        if (currentInFile != SettingsConfigVersion.CURRENT) {
            root.put("configVersion", SettingsConfigVersion.CURRENT);
            changed = true;
        }

        if (!changed) {
            return;
        }

        final Path pluginDir = Objects.requireNonNull(settingsPath.getParent(), "settings path has no parent");
        final Path backupDir = pluginDir.resolve(MIGRATION_BACKUP_DIR);
        Files.createDirectories(backupDir);
        final String backupName = settingsPath.getFileName().toString() + ".backup-" + Instant.now().getEpochSecond() + ".yml";
        final Path backup = backupDir.resolve(backupName);
        Files.copy(settingsPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Backed up settings.yml to " + MIGRATION_BACKUP_DIR + "/" + backup.getFileName());

        dumpYaml(root, settingsPath);
        log.info("Wrote settings.yml (configVersion " + SettingsConfigVersion.CURRENT + "; migration and/or YAML normalization).");
    }

    private static final Load YAML_LOAD = new Load(LoadSettings.builder().build());
    private static final Dump YAML_DUMP = new Dump(DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .build());

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlMap(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final Object loaded = YAML_LOAD.loadFromReader(reader);
            if (loaded instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return null;
        }
    }

    private static void dumpYaml(Map<String, Object> root, Path path) throws IOException {
        @SuppressWarnings("unchecked")
        final Map<String, Object> withoutNulls = (Map<String, Object>) Objects.requireNonNull(stripNullEntriesDeep(root));
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(YAML_DUMP.dumpToString(withoutNulls));
        }
    }

    /**
     * Omits {@code key: null} everywhere so migrated YAML matches {@code outputNulls(false)} from ConfigLib.
     */
    @SuppressWarnings("unchecked")
    private static Object stripNullEntriesDeep(Object node) {
        if (node instanceof Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                out.put(String.valueOf(e.getKey()), stripNullEntriesDeep(e.getValue()));
            }
            return out;
        }
        if (node instanceof List<?> list) {
            final List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                out.add(stripNullEntriesDeep(item));
            }
            return out;
        }
        return node;
    }

    private static int parseDeclaredVersion(Map<String, Object> root) {
        final Object v = root.get("configVersion");
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasLegacyFlatNameTags(Map<String, Object> root) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        for (Object raw : nt.values()) {
            if (raw instanceof Map<?, ?> tag) {
                if (tag.containsKey("lines")
                        && !tag.containsKey("linesGroups")
                        && !tag.containsKey("displayGroups")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasStringLineDisplayGroups(Map<String, Object> root) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        for (Object raw : nt.values()) {
            if (!(raw instanceof Map<?, ?> tag)) {
                continue;
            }
            final Object groupsObj = tag.containsKey("displayGroups") ? tag.get("displayGroups") : tag.get("linesGroups");
            if (!(groupsObj instanceof List<?> groups)) {
                continue;
            }
            for (Object groupObj : groups) {
                if (!(groupObj instanceof Map<?, ?> group)) {
                    continue;
                }
                final Object linesObj = group.get("lines");
                if (!(linesObj instanceof List<?> lines)) {
                    continue;
                }
                for (Object line : lines) {
                    if (!(line instanceof Map<?, ?>)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Obsolete YAML key from unreleased dev builds; same schema version as {@code displayGroups}.
     */
    @SuppressWarnings("unchecked")
    private static boolean renameObsoleteLinesGroupsKey(Map<String, Object> root, Logger log) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        boolean any = false;
        for (Map.Entry<?, ?> e : nt.entrySet()) {
            final String key = String.valueOf(e.getKey());
            final Object raw = e.getValue();
            if (!(raw instanceof Map)) {
                continue;
            }
            final Map<String, Object> tag = (Map<String, Object>) raw;
            if (!tag.containsKey("linesGroups")) {
                continue;
            }
            if (tag.containsKey("displayGroups")) {
                log.warning("NameTag '" + key + "' has both linesGroups and displayGroups; keeping displayGroups, removing linesGroups.");
                tag.remove("linesGroups");
            } else {
                tag.put("displayGroups", tag.remove("linesGroups"));
            }
            any = true;
        }
        if (any) {
            log.info("Renamed nameTags.*.linesGroups → displayGroups (obsolete key, still configVersion " + SettingsConfigVersion.CURRENT + ").");
        }
        return any;
    }

    /**
     * Removes deprecated {@code modifiers} from each display group. Drops {@code lines} on ITEM/BLOCK rows (material fields only).
     */
    @SuppressWarnings("unchecked")
    private static boolean stripObsoleteDisplayGroupFields(Map<String, Object> root, Logger log) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        boolean any = false;
        for (Object raw : nt.values()) {
            if (!(raw instanceof Map)) {
                continue;
            }
            final Map<String, Object> tag = (Map<String, Object>) raw;
            final Object dg = tag.get("displayGroups");
            if (!(dg instanceof List<?> list)) {
                continue;
            }
            for (Object gObj : list) {
                if (!(gObj instanceof Map)) {
                    continue;
                }
                final Map<String, Object> g = (Map<String, Object>) gObj;
                if (g.remove("modifiers") != null) {
                    any = true;
                }
                final Object dt = g.get("displayType");
                if (dt instanceof String s) {
                    final String u = s.trim().toUpperCase(java.util.Locale.ROOT);
                    if (("ITEM".equals(u) || "BLOCK".equals(u)) && g.remove("lines") != null) {
                        any = true;
                    }
                }
            }
        }
        if (any) {
            log.info("Normalized displayGroups: removed obsolete modifiers and lines on ITEM/BLOCK rows.");
        }
        return any;
    }

    /**
     * Removes {@code background} when it is a disabled transparent integer preset (same as omitting the key).
     */
    @SuppressWarnings("unchecked")
    private static boolean stripRedundantDisplayBackgrounds(Map<String, Object> root, Logger log) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        boolean any = false;
        for (Object raw : nt.values()) {
            if (!(raw instanceof Map)) {
                continue;
            }
            final Map<String, Object> tag = (Map<String, Object>) raw;
            final Object dg = tag.get("displayGroups");
            if (!(dg instanceof List<?> list)) {
                continue;
            }
            for (Object gObj : list) {
                if (!(gObj instanceof Map)) {
                    continue;
                }
                final Map<String, Object> g = (Map<String, Object>) gObj;
                final Object bg = g.get("background");
                if (!(bg instanceof Map)) {
                    continue;
                }
                if (isRedundantIntegerBackgroundYaml((Map<String, Object>) bg)) {
                    g.remove("background");
                    any = true;
                }
            }
        }
        if (any) {
            log.info("Normalized displayGroups: removed redundant default integer backgrounds (same as omitting background).");
        }
        return any;
    }

    private static boolean isRedundantIntegerBackgroundYaml(Map<String, Object> m) {
        final Object type = m.get("type");
        if (type != null && !"integer".equalsIgnoreCase(String.valueOf(type))) {
            return false;  // hex type is never redundant-omitted
        }
        if (!yamlFalsy(m.get("enabled"))) {
            return false;
        }
        if (yamlInt(m.get("opacity"), -1) != 0) {
            return false;
        }
        if (!yamlFalsy(m.get("shadowed"))) {
            return false;
        }
        // Post-v4 migration: color field instead of red/green/blue
        if (m.containsKey("color")) {
            final String color = String.valueOf(m.get("color")).trim();
            return isBlackColorString(color);
        }
        // Pre-v4: red/green/blue fields
        if (yamlInt(m.get("red"), -1) != 0 || yamlInt(m.get("green"), -1) != 0 || yamlInt(m.get("blue"), -1) != 0) {
            return false;
        }
        return true;
    }

    private static boolean isBlackColorString(String color) {
        if (color == null || color.isEmpty()) return true;
        if (color.startsWith("#")) {
            try {
                return Integer.parseInt(color.substring(1), 16) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        final String[] parts = color.split(",");
        if (parts.length != 3) return false;
        try {
            return Integer.parseInt(parts[0].trim()) == 0
                    && Integer.parseInt(parts[1].trim()) == 0
                    && Integer.parseInt(parts[2].trim()) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean yamlFalsy(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return !b;
        }
        if (o instanceof String s) {
            return "false".equalsIgnoreCase(s.trim()) || "no".equalsIgnoreCase(s.trim()) || "off".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    private static int yamlInt(Object o, int missing) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return missing;
            }
        }
        return missing;
    }

    /**
     * Converts legacy v1 {@code NameTag} (top-level {@code lines} + {@code background} / {@code scale}) into {@code displayGroups}.
     */
    @SuppressWarnings("unchecked")
    private static boolean migrateV1ToV2(Map<String, Object> root, Logger log) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        boolean any = false;
        for (Map.Entry<?, ?> e : nt.entrySet()) {
            final String key = String.valueOf(e.getKey());
            final Object raw = e.getValue();
            if (!(raw instanceof Map)) {
                continue;
            }
            final Map<String, Object> tag = (Map<String, Object>) raw;
            if (tag.containsKey("displayGroups") || tag.containsKey("linesGroups") || !tag.containsKey("lines")) {
                continue;
            }

            final Object linesObj = tag.remove("lines");
            if (!(linesObj instanceof List<?> linesList)) {
                log.warning("NameTag '" + key + "' has invalid 'lines'; skipping this entry.");
                continue;
            }

            final List<String> lines = new ArrayList<>();
            for (Object line : linesList) {
                lines.add(line == null ? "" : String.valueOf(line));
            }

            final Map<String, Object> group = new LinkedHashMap<>();
            group.put("lines", lines);

            if (tag.containsKey("background")) {
                group.put("background", tag.remove("background"));
            }
            if (tag.containsKey("scale")) {
                group.put("scale", tag.remove("scale"));
            } else {
                group.put("scale", 1.0);
            }
            if (tag.containsKey("yOffset")) {
                group.put("yOffset", tag.remove("yOffset"));
            } else {
                group.put("yOffset", 1.0);
            }
            final Object whenVal = tag.remove("when");
            if (whenVal != null) {
                group.put("when", whenVal);
            }

            final List<Map<String, Object>> groups = new ArrayList<>();
            groups.add(group);
            tag.put("displayGroups", groups);

            log.info("Migrated NameTag '" + key + "' from flat lines (v1) to displayGroups.");
            any = true;
        }
        return any;
    }

    /**
     * Converts v3 (structured lines + polymorphic background) to v4:
     * <ol>
     *   <li>Background: removes {@code type}/{@code red}/{@code green}/{@code blue}/{@code hex} fields,
     *       adds unified {@code color} field (RGB as {@code "R,G,B"} or hex as {@code "#RRGGBB"}).</li>
     *   <li>Settings: moves known flat top-level fields into {@code behavior}, {@code visibility},
     *       {@code performance} sub-maps.</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private static boolean migrateV3ToV4(Map<String, Object> root, Logger log) {
        boolean changed = false;

        // 1. Convert backgrounds
        final Object nameTags = root.get("nameTags");
        if (nameTags instanceof Map<?, ?> nt) {
            for (Object rawTag : nt.values()) {
                if (!(rawTag instanceof Map)) continue;
                final Map<String, Object> tag = (Map<String, Object>) rawTag;
                final Object dg = tag.get("displayGroups");
                if (!(dg instanceof List<?> list)) continue;
                for (Object gObj : list) {
                    if (!(gObj instanceof Map)) continue;
                    final Map<String, Object> g = (Map<String, Object>) gObj;
                    final Object bg = g.get("background");
                    if (!(bg instanceof Map)) continue;
                    final Map<String, Object> bgMap = (Map<String, Object>) bg;
                    final Object type = bgMap.get("type");
                    if (type == null) continue;
                    final String typeStr = String.valueOf(type).trim().toLowerCase(java.util.Locale.ROOT);
                    if ("integer".equals(typeStr)) {
                        final int r = yamlInt(bgMap.remove("red"), 0);
                        final int gr = yamlInt(bgMap.remove("green"), 0);
                        final int bl = yamlInt(bgMap.remove("blue"), 0);
                        bgMap.remove("type");
                        bgMap.put("color", r + "," + gr + "," + bl);
                        changed = true;
                    } else if ("hex".equals(typeStr)) {
                        final Object hexVal = bgMap.remove("hex");
                        bgMap.remove("type");
                        bgMap.put("color", hexVal != null ? String.valueOf(hexVal) : "#000000");
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            log.info("Migrated backgrounds from polymorphic type:integer/hex to unified color field.");
        }

        // 2. Section flat settings
        final List<String> behaviorKeys = List.of("taskInterval", "displayAnimationInterval", "yOffset", "viewDistance",
                "compactDisplayGroupStack", "displayGroupLineHeightBlocks", "disableDefaultNameTag",
                "forceDisableDefaultNameTag", "defaultBillboard", "format", "removeEmptyLines");
        final List<String> visibilityKeys = List.of("sneakOpacity", "showWhileLooking", "showCurrentNameTag",
                "allowPerPlayerShowOwnWhenGlobalDisabled", "obscuredNametagThroughWalls", "obscuredNametagOpacity",
                "obscuredNametagMaxDistance", "obscuredNametagCheckInterval");
        final List<String> performanceKeys = List.of("componentCaching", "placeholderCacheTime", "enableRelationalPlaceholders",
                "placeholderUpdateRates");

        changed |= sectionalizeSettings(root, "behavior", behaviorKeys, log);
        changed |= sectionalizeSettings(root, "visibility", visibilityKeys, log);
        changed |= sectionalizeSettings(root, "performance", performanceKeys, log);

        return changed;
    }

    private static boolean sectionalizeSettings(Map<String, Object> root, String sectionName,
                                                List<String> keys, Logger log) {
        boolean any = false;
        for (String key : keys) {
            if (!root.containsKey(key)) continue;
            if (!any) {
                root.computeIfAbsent(sectionName, k -> new LinkedHashMap<String, Object>());
                any = true;
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> section = (Map<String, Object>) root.get(sectionName);
            if (!section.containsKey(key)) {
                section.put(key, root.remove(key));
            } else {
                root.remove(key);
            }
        }
        if (any) {
            log.info("Moved settings fields to " + sectionName + " section.");
        }
        return any;
    }

    /**
     * Converts v2 text rows from {@code lines: [string, ...]} to structured rows:
     * {@code lines: [{text: string}, ...]}. This keeps one text display per display group while allowing per-line metadata.
     */
    @SuppressWarnings("unchecked")
    private static boolean migrateV2ToV3(Map<String, Object> root, Logger log) {
        final Object nameTags = root.get("nameTags");
        if (!(nameTags instanceof Map<?, ?> nt)) {
            return false;
        }
        boolean any = false;
        for (Map.Entry<?, ?> entry : nt.entrySet()) {
            final String tagKey = String.valueOf(entry.getKey());
            final Object rawTag = entry.getValue();
            if (!(rawTag instanceof Map)) {
                continue;
            }
            final Map<String, Object> tag = (Map<String, Object>) rawTag;
            final Object dg = tag.get("displayGroups");
            if (!(dg instanceof List<?> groups)) {
                continue;
            }
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                final Object rawGroup = groups.get(groupIndex);
                if (!(rawGroup instanceof Map)) {
                    continue;
                }
                final Map<String, Object> group = (Map<String, Object>) rawGroup;
                final Object linesObj = group.get("lines");
                if (!(linesObj instanceof List<?> rawLines)) {
                    continue;
                }

                final List<Object> migratedLines = new ArrayList<>(rawLines.size());
                boolean groupChanged = false;
                for (Object rawLine : rawLines) {
                    if (rawLine instanceof Map<?, ?> existingLine) {
                        final Map<String, Object> line = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> lineEntry : existingLine.entrySet()) {
                            line.put(String.valueOf(lineEntry.getKey()), lineEntry.getValue());
                        }
                        if (!line.containsKey("text") && line.containsKey("line")) {
                            line.put("text", line.remove("line"));
                            groupChanged = true;
                        }
                        if (!line.containsKey("text")) {
                            log.warning("NameTag '" + tagKey + "' displayGroups[" + groupIndex + "] has a structured line without 'text'.");
                            line.put("text", "");
                            groupChanged = true;
                        } else if (!(line.get("text") instanceof String)) {
                            line.put("text", String.valueOf(line.get("text")));
                            groupChanged = true;
                        }
                        migratedLines.add(line);
                        continue;
                    }

                    final Map<String, Object> line = new LinkedHashMap<>();
                    line.put("text", rawLine == null ? "" : String.valueOf(rawLine));
                    migratedLines.add(line);
                    groupChanged = true;
                }

                if (groupChanged) {
                    group.put("lines", migratedLines);
                    any = true;
                }
            }
        }
        if (any) {
            log.info("Migrated displayGroups lines from raw strings (v2) to structured text rows (v3).");
        }
        return any;
    }

    /**
     * YAML 1.1 (SnakeYAML / Bukkit) parses unquoted {@code Yes}/{@code No}/{@code On}/{@code Off} as booleans.
     * {@link Settings.PlaceholderReplacement#placeholder()} must be a string (e.g. PAPI output {@code Yes}/{@code No}).
     */
    @SuppressWarnings("unchecked")
    static boolean sanitizePlaceholdersReplacements(@NotNull Map<String, Object> root, @NotNull Logger log) {
        final Object pr = root.get("placeholdersReplacements");
        if (!(pr instanceof Map<?, ?> outer)) {
            return false;
        }
        boolean changed = false;
        for (Object listObj : outer.values()) {
            if (!(listObj instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (!(item instanceof Map)) {
                    continue;
                }
                final Map<String, Object> row = (Map<String, Object>) item;
                final Object ph = row.get("placeholder");
                if (ph instanceof Boolean b) {
                    row.put("placeholder", b ? "Yes" : "No");
                    changed = true;
                } else if (ph != null && !(ph instanceof String)) {
                    row.put("placeholder", String.valueOf(ph));
                    changed = true;
                }
                final Object rep = row.get("replacement");
                if (rep != null && !(rep instanceof String)) {
                    row.put("replacement", String.valueOf(rep));
                    changed = true;
                }
            }
        }
        if (changed) {
            log.info("Normalized placeholdersReplacements (boolean or non-string placeholder/replacement → string). "
                    + "Quote values in YAML, e.g. placeholder: \"Yes\", to avoid YAML 1.1 boolean parsing.");
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean migrateV4ToV5(Map<String, Object> root, Logger log) {
        final Object visibilityObj = root.get("visibility");
        if (!(visibilityObj instanceof Map)) {
            return false;
        }
        final Map<String, Object> visibility = (Map<String, Object>) visibilityObj;
        boolean changed = false;

        final Object obscuredEnabled = visibility.remove("obscuredNametagThroughWalls");
        final Object opacity = visibility.remove("obscuredNametagOpacity");
        final Object maxDistance = visibility.remove("obscuredNametagMaxDistance");
        final Object checkInterval = visibility.remove("obscuredNametagCheckInterval");

        if (obscuredEnabled != null) {
            final boolean enabled = !yamlFalsy(obscuredEnabled);
            visibility.put("throughWallMode", enabled ? "OBSCURED" : "SEE_THROUGH");
            changed = true;
        }

        final Map<String, Object> settings = new LinkedHashMap<>();
        if (opacity != null) {
            settings.put("opacity", opacity);
            changed = true;
        }
        if (maxDistance != null) {
            settings.put("maxDistance", maxDistance);
            changed = true;
        }
        if (checkInterval != null) {
            settings.put("checkInterval", checkInterval);
            changed = true;
        }

        if (!settings.isEmpty() || obscuredEnabled != null) {
            visibility.put("throughWallSettings", settings);
            changed = true;
        }

        if (changed) {
            log.info("Migrated visibility settings to throughWallMode and throughWallSettings (v5).");
        }
        return changed;
    }

    private static boolean migrateV5ToV6(Map<String, Object> root, Logger log) {
        final Object existing = root.get("glowAnimations");
        if (existing instanceof Map<?, ?> map && !map.isEmpty()) {
            return false;
        }
        root.put("glowAnimations", defaultGlowAnimationsYaml());
        log.info("Added default glowAnimations presets (v6).");
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean migrateV6ToV7(Map<String, Object> root, Logger log) {
        final Map<String, Object> performance = (Map<String, Object>) root.computeIfAbsent("performance",
                ignored -> new LinkedHashMap<>());
        if (performance.containsKey("distanceRefreshCulling")) {
            return false;
        }
        final Map<String, Object> culling = new LinkedHashMap<>();
        culling.put("enabled", true);
        culling.put("nearDistance", 24.0);
        culling.put("maxDistance", 96.0);
        culling.put("maxInterval", 100);
        culling.put("curve", 2.0);
        performance.put("distanceRefreshCulling", culling);
        log.info("Added distance refresh culling settings (v7).");
        return true;
    }

    private static Map<String, Object> defaultGlowAnimationsYaml() {
        final Map<String, Object> presets = new LinkedHashMap<>();
        presets.put("rainbow", Map.of("type", "rainbow", "speed", 1.0));
        final Map<String, Object> gradient = new LinkedHashMap<>();
        gradient.put("type", "gradient");
        gradient.put("colors", List.of("#FF5555", "#55FF55", "#5555FF"));
        gradient.put("refreshInterval", 10);
        presets.put("gradient", gradient);
        final Map<String, Object> goldPulse = new LinkedHashMap<>();
        goldPulse.put("type", "custom");
        goldPulse.put("id", "default_gold_pulse");
        goldPulse.put("speed", 1.0);
        presets.put("gold_pulse", goldPulse);
        return presets;
    }
}

package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

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
        if (declared == 3) {
            log.info("settings.yml had unreleased configVersion 3; normalized to " + SettingsConfigVersion.CURRENT + ".");
            root.put("configVersion", SettingsConfigVersion.CURRENT);
            declared = SettingsConfigVersion.CURRENT;
            changed = true;
        }

        final boolean legacy = hasLegacyFlatNameTags(root);
        final int from;
        if (declared > 0) {
            from = declared;
        } else if (legacy) {
            from = SettingsConfigVersion.LEGACY_FLAT_NAMETAG;
        } else {
            from = SettingsConfigVersion.CURRENT;
        }

        if (from > SettingsConfigVersion.CURRENT) {
            log.warning("settings.yml configVersion " + from + " is newer than this plugin supports ("
                    + SettingsConfigVersion.CURRENT + "); not modifying the file.");
            return;
        }

        changed |= sanitizePlaceholdersReplacements(root, log);
        changed |= stripObsoleteDisplayGroupFields(root, log);
        changed |= stripRedundantDisplayBackgrounds(root, log);
        changed |= renameObsoleteLinesGroupsKey(root, log);
        for (int v = from; v < SettingsConfigVersion.CURRENT; v++) {
            switch (v) {
                case 1 -> changed |= migrateV1ToV2(root, log);
                default -> throw new IllegalStateException("Missing settings migrator from v" + v + " to v" + (v + 1));
            }
        }

        final Object currentDeclared = root.get("configVersion");
        final int currentInFile = currentDeclared instanceof Number n ? n.intValue() : 0;
        if (currentInFile != SettingsConfigVersion.CURRENT) {
            root.put("configVersion", SettingsConfigVersion.CURRENT);
            changed = true;
        }

        if (!changed) {
            return;
        }

        final Path backup = settingsPath.resolveSibling(
                settingsPath.getFileName().toString() + ".backup-" + Instant.now().getEpochSecond() + ".yml");
        Files.copy(settingsPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Backed up settings.yml to " + backup.getFileName());

        dumpYaml(root, settingsPath);
        log.info("Wrote settings.yml (configVersion " + SettingsConfigVersion.CURRENT + "; migration and/or YAML normalization).");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlMap(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return null;
        }
    }

    private static void dumpYaml(Map<String, Object> root, Path path) throws IOException {
        @SuppressWarnings("unchecked")
        final Map<String, Object> withoutNulls = (Map<String, Object>) Objects.requireNonNull(stripNullEntriesDeep(root));
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        final Yaml yaml = new Yaml(options);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            yaml.dump(withoutNulls, writer);
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
            return false;
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
        if (yamlInt(m.get("red"), -1) != 0 || yamlInt(m.get("green"), -1) != 0 || yamlInt(m.get("blue"), -1) != 0) {
            return false;
        }
        return true;
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
}

package org.alexdev.unlimitednametags.config;

import com.google.common.collect.Maps;
import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

@Getter
public class ConfigManager {

    private static final YamlConfigurationProperties PROPERTIES = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .charset(StandardCharsets.UTF_8)
            .outputNulls(false)
            .inputNulls(false)
            .footer("Authors: AlexDev_")
            .build();

    private final UnlimitedNameTags plugin;
    private Settings settings;
    private Advanced advanced = new Advanced();
    private boolean compiled;

    public ConfigManager(@NotNull final UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.loadUsage();
    }

    private void loadUsage() {
        try (@NotNull final InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"));
             final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            compiled = YamlConfiguration.loadConfiguration(reader).getBoolean("compiled", true);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public Optional<Throwable> loadConfigs() {
        final File settingsFile = new File(plugin.getDataFolder(), "settings.yml");

        try {
            final Path settingsPath = settingsFile.toPath();
            SettingsYamlMigrator.migrateIfNeeded(settingsPath, plugin.getLogger());
            settings = YamlConfigurations.update(
                    settingsPath,
                    Settings.class,
                    PROPERTIES
            );
            checkData();
            loadAdvancedConfig(false);
            return Optional.empty();
        } catch (final Exception e) {
            return Optional.of(e);
        }
    }

    public void reload() {
        final Path settingsPath = new File(plugin.getDataFolder(), "settings.yml").toPath();
        try {
            SettingsYamlMigrator.migrateIfNeeded(settingsPath, plugin.getLogger());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate settings.yml on reload", e);
        }
        settings = YamlConfigurations.load(settingsPath, Settings.class, PROPERTIES);
        checkData();
        loadAdvancedConfig(true);
    }

    @NotNull
    public Advanced getAdvanced() {
        return advanced;
    }

    private void loadAdvancedConfig(boolean keepPreviousOnFailure) {
        final File file = new File(plugin.getDataFolder(), "advanced.yml");
        if (!file.exists()) {
            advanced = new Advanced();
            return;
        }
        try {
            advanced = YamlConfigurations.load(file.toPath(), Advanced.class, PROPERTIES);
            validateAdvancedRules();
            plugin.getLogger().info("Loaded optional advanced.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load advanced.yml", e);
            if (!keepPreviousOnFailure) {
                advanced = new Advanced();
            }
        }
    }

    private void validateAdvancedRules() {
        final List<Advanced.HelmetHeightRule> rules = advanced.getHelmetHeightRules();
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            final Advanced.HelmetHeightRule rule = rules.get(i);
            if (!rule.definesItemMatch()) {
                plugin.getLogger().warning("advanced.yml helmetHeightRules[" + i + "] has no item matcher; it will never apply.");
            }
            if (rule.getHeight() <= 0) {
                plugin.getLogger().warning("advanced.yml helmetHeightRules[" + i + "] height must be > 0; it will never apply.");
            }
            final boolean minSet = rule.getCustomModelDataMin() != null;
            final boolean maxSet = rule.getCustomModelDataMax() != null;
            if (minSet != maxSet) {
                plugin.getLogger().warning("advanced.yml helmetHeightRules[" + i + "] must set both customModelDataMin and customModelDataMax for a CMD range.");
            }
            if (minSet && maxSet && rule.getCustomModelData() != null) {
                plugin.getLogger().warning("advanced.yml helmetHeightRules[" + i + "] customModelData is ignored when customModelDataMin/Max range is set.");
            }
            if (rule.getMaterial() != null && !rule.getMaterial().isEmpty()
                    && Material.matchMaterial(rule.getMaterial(), false) == null) {
                plugin.getLogger().warning("advanced.yml helmetHeightRules[" + i + "] unknown material: " + rule.getMaterial());
            }
        }
    }

    private void checkData() {
        if (settings == null) {
            throw new IllegalStateException("Settings not loaded");
        }

        if (settings.getConfigVersion() != SettingsConfigVersion.CURRENT) {
            plugin.getLogger().warning("settings.yml configVersion is " + settings.getConfigVersion() + " but this build expects "
                    + SettingsConfigVersion.CURRENT + "; reload after backup or migrate the file.");
        }

        if (settings.getDefaultNameTag().isEmpty()) {
            throw new IllegalStateException("Default name tag is empty");
        }

        final Map<String, Settings.NameTag> nameTags = Maps.newLinkedHashMap();
        boolean save = false;

        if (settings.getObscuredNametagCheckInterval() < 1) {
            plugin.getLogger().warning("obscuredNametagCheckInterval must be >= 1; resetting to 1.");
            settings.setObscuredNametagCheckInterval(1);
            save = true;
        }
        if (settings.getObscuredNametagMaxDistance() <= 0.0) {
            plugin.getLogger().warning("obscuredNametagMaxDistance must be > 0; resetting to 48.");
            settings.setObscuredNametagMaxDistance(48.0);
            save = true;
        }

        for (final Map.Entry<String, Settings.NameTag> entry : settings.getNameTags().entrySet()) {
            final Settings.NameTag nameTag = entry.getValue();
            boolean entryFixed = false;
            final ArrayList<Settings.DisplayGroup> fixedGroups = new ArrayList<>(nameTag.displayGroups().size());
            for (final Settings.DisplayGroup group : nameTag.displayGroups()) {
                if (group.scale() <= 0f) {
                    plugin.getLogger().warning("NameTag '" + entry.getKey() + "': display group scale is <= 0; persisted as 1.0.");
                    fixedGroups.add(group.withScale(1f));
                    entryFixed = true;
                } else {
                    fixedGroups.add(group);
                }
            }
            if (entryFixed) {
                nameTags.put(entry.getKey(), new Settings.NameTag(nameTag.permission(), List.copyOf(fixedGroups)));
                save = true;
            } else {
                nameTags.put(entry.getKey(), nameTag);
            }
        }

        if (save) {
            settings.getNameTags().clear();
            settings.getNameTags().putAll(nameTags);
            save();
        }

    }

    public void save() {
        YamlConfigurations.save(new File(plugin.getDataFolder(), "settings.yml").toPath(), Settings.class, settings, PROPERTIES);
    }
}

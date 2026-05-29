package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

@Getter
public class ConfigManager {

    private final UnlimitedNameTags plugin;
    private Settings settings;
    private volatile Advanced advanced = new Advanced();
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
                    UntYamlConfiguration.PROPERTIES
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
        settings = YamlConfigurations.load(settingsPath, Settings.class, UntYamlConfiguration.PROPERTIES);
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
            sortHelmetHeightRules();
            return;
        }
        try {
            advanced = YamlConfigurations.load(file.toPath(), Advanced.class, UntYamlConfiguration.PROPERTIES);
            AdvancedConfigValidator.validate(advanced, msg -> plugin.getLogger().warning(msg));
            sortHelmetHeightRules();
            plugin.getLogger().info("Loaded optional advanced.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load advanced.yml", e);
            if (!keepPreviousOnFailure) {
                advanced = new Advanced();
                sortHelmetHeightRules();
            }
        }
    }

    private void sortHelmetHeightRules() {
        final List<Advanced.HelmetHeightRule> rules = advanced.getHelmetHeightRules();
        if (rules == null || rules.size() < 2) {
            return;
        }
        rules.sort(Comparator.comparingInt(Advanced.HelmetHeightRule::getPriority).reversed());
    }

    private void checkData() {
        if (settings == null) {
            throw new IllegalStateException("Settings not loaded");
        }
        if (SettingsValidator.validateAndFix(settings, msg -> plugin.getLogger().warning(msg))) {
            save();
        }
    }

    public void save() {
        YamlConfigurations.save(new File(plugin.getDataFolder(), "settings.yml").toPath(), Settings.class, settings, UntYamlConfiguration.PROPERTIES);
    }
}

package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Getter
public class ConfigManager {

    private final UnlimitedNameTags plugin;
    private Settings settings;

    public ConfigManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public Optional<Throwable> loadConfigs() {
        final YamlConfigurationProperties properties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
                .charset(StandardCharsets.UTF_8)
                .outputNulls(true)
                .inputNulls(false)
                .footer("Authors: AlexDev_")
                .build();
        final File settingsFile = new File(plugin.getDataFolder(), "settings.yml");

        try {
            settings = YamlConfigurations.update(
                    settingsFile.toPath(),
                    Settings.class,
                    properties
            );
            checkData();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e);
        }
    }

    public void reload() {
        final YamlConfigurationProperties properties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
                .charset(StandardCharsets.UTF_8)
                .outputNulls(true)
                .inputNulls(false)
                .footer("Authors: AlexDev_")
                .build();
        settings = YamlConfigurations.load(new File(plugin.getDataFolder(), "settings.yml").toPath(), Settings.class, properties);
        checkData();
    }

    private void checkData() {
        if (settings == null) {
            throw new IllegalStateException("Settings not loaded");
        }

        if (settings.getDefaultNameTag().isEmpty()) {
            throw new IllegalStateException("Default name tag is empty");
        }

    }
}

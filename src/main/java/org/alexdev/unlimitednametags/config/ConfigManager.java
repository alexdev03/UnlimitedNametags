package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;

import java.io.File;

@Getter
public class ConfigManager {

    private final UnlimitedNameTags plugin;
    private Settings settings;

    public ConfigManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        final YamlConfigurationProperties properties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
                .footer("Authors: AlexDev_")
                .build();
        final File settingsFile = new File(plugin.getDataFolder(), "settings.yml");

        settings = YamlConfigurations.update(
                settingsFile.toPath(),
                Settings.class,
                properties
        );
    }

    public void reload() {
        final YamlConfigurationProperties properties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
                .footer("Authors: AlexDev_")
                .build();
        settings = YamlConfigurations.load(new File(plugin.getDataFolder(), "settings.yml").toPath(), Settings.class, properties);
    }
}

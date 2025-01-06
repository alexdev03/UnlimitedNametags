package org.alexdev.unlimitednametags.config;

import com.google.common.collect.Maps;
import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Getter
public class ConfigManager {

    private static final YamlConfigurationProperties PROPERTIES = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .charset(StandardCharsets.UTF_8)
            .outputNulls(true)
            .inputNulls(false)
            .footer("Authors: AlexDev_")
            .build();

    private final UnlimitedNameTags plugin;
    private Settings settings;
    private boolean compiled;

    public ConfigManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.loadUsage();
    }

    private void loadUsage() {
        try (@NotNull final InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"));
             final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            compiled = YamlConfiguration.loadConfiguration(reader).getBoolean("compiled", true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public Optional<Throwable> loadConfigs() {
        final File settingsFile = new File(plugin.getDataFolder(), "settings.yml");

        try {
            settings = YamlConfigurations.update(
                    settingsFile.toPath(),
                    Settings.class,
                    PROPERTIES
            );
            checkData();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e);
        }
    }

    public void reload() {
        settings = YamlConfigurations.load(new File(plugin.getDataFolder(), "settings.yml").toPath(), Settings.class, PROPERTIES);
        checkData();
    }

    private void checkData() {
        if (settings == null) {
            throw new IllegalStateException("Settings not loaded");
        }

        if (settings.getDefaultNameTag().isEmpty()) {
            throw new IllegalStateException("Default name tag is empty");
        }

        final Map<String, Settings.NameTag> nameTags = Maps.newLinkedHashMap();
        boolean save = false;

        for (Map.Entry<String, Settings.NameTag> entry : settings.getNameTags().entrySet()) {
            final Settings.NameTag nameTag = entry.getValue();
            if (nameTag.scale() <= 0) {
                plugin.getLogger().warning("Nametag scale is less than or equal to 0");
                nameTags.put(entry.getKey(), new Settings.NameTag(nameTag.permission(), nameTag.linesGroups(), nameTag.background(), 1f));
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

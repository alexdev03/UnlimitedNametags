package org.alexdev.unlimitednametags.data;

import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent per-player glow overrides ({@code unlimitednametags:glow_overrides}).
 */
public final class NametagPlayerGlowStorage {

    private final NamespacedKey glowOverridesKey;
    private final Logger logger;

    public NametagPlayerGlowStorage(@NotNull org.bukkit.plugin.Plugin plugin) {
        this.glowOverridesKey = new NamespacedKey(plugin, "glow_overrides");
        this.logger = plugin.getLogger();
    }

    @NotNull
    public Map<Integer, GlowOverride> read(@NotNull Player player) {
        final String yaml = player.getPersistentDataContainer().get(glowOverridesKey, PersistentDataType.STRING);
        try {
            final StoredGlowOverrides stored = NametagPdcYamlCodec.decode(yaml, StoredGlowOverrides.class);
            if (stored == null || stored.overrides == null || stored.overrides.isEmpty()) {
                return Map.of();
            }
            final Map<Integer, GlowOverride> result = new LinkedHashMap<>();
            stored.overrides.forEach((key, value) -> {
                try {
                    result.put(Integer.parseInt(key), value);
                } catch (NumberFormatException ignored) {
                    logger.warning("Invalid glow override index in PDC for " + player.getName() + ": " + key);
                }
            });
            return Map.copyOf(result);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read glow overrides from PDC for " + player.getName(), e);
            return Map.of();
        }
    }

    public void write(@NotNull Player player, @NotNull Map<Integer, GlowOverride> overrides) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (overrides.isEmpty()) {
            pdc.remove(glowOverridesKey);
            return;
        }
        final StoredGlowOverrides stored = new StoredGlowOverrides();
        overrides.forEach((index, glow) -> stored.overrides.put(String.valueOf(index), glow));
        try {
            pdc.set(glowOverridesKey, PersistentDataType.STRING,
                    NametagPdcYamlCodec.encode(stored, StoredGlowOverrides.class));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write glow overrides to PDC for " + player.getName(), e);
        }
    }

    public void clearGroup(@NotNull Player player, int groupIndex) {
        final Map<Integer, GlowOverride> current = new LinkedHashMap<>(read(player));
        if (current.remove(groupIndex) != null) {
            write(player, current);
        }
    }

    public void clearAll(@NotNull Player player) {
        player.getPersistentDataContainer().remove(glowOverridesKey);
    }

    @Configuration
    @Getter
    @NoArgsConstructor
    static final class StoredGlowOverrides {
        private Map<String, GlowOverride> overrides = new LinkedHashMap<>();
    }
}

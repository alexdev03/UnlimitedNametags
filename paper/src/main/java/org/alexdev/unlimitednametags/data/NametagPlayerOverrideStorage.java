package org.alexdev.unlimitednametags.data;

import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistent per-player nametag layout overrides and display-group animation fragments.
 */
public final class NametagPlayerOverrideStorage {

    private final NamespacedKey nametagOverrideKey;
    private final NamespacedKey displayAnimationsKey;
    private final Logger logger;

    public NametagPlayerOverrideStorage(@NotNull org.bukkit.plugin.Plugin plugin) {
        this.nametagOverrideKey = new NamespacedKey(plugin, "nametag_override");
        this.displayAnimationsKey = new NamespacedKey(plugin, "display_animations");
        this.logger = plugin.getLogger();
    }

    @NotNull
    public Optional<Settings.NameTag> readNametagOverride(@NotNull Player player) {
        final String yaml = player.getPersistentDataContainer().get(nametagOverrideKey, PersistentDataType.STRING);
        try {
            final StoredNametagOverride stored = NametagPdcYamlCodec.decode(yaml, StoredNametagOverride.class);
            return stored == null || stored.nameTag == null ? Optional.empty() : Optional.of(stored.nameTag);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read nametag override from PDC for " + player.getName(), e);
            return Optional.empty();
        }
    }

    public void writeNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        final StoredNametagOverride stored = new StoredNametagOverride();
        stored.nameTag = nameTag;
        try {
            player.getPersistentDataContainer().set(nametagOverrideKey, PersistentDataType.STRING,
                    NametagPdcYamlCodec.encode(stored, StoredNametagOverride.class));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write nametag override to PDC for " + player.getName(), e);
        }
    }

    public void clearNametagOverride(@NotNull Player player) {
        player.getPersistentDataContainer().remove(nametagOverrideKey);
    }

    @NotNull
    public Map<Integer, DisplayAnimation> readDisplayAnimations(@NotNull Player player) {
        final String yaml = player.getPersistentDataContainer().get(displayAnimationsKey, PersistentDataType.STRING);
        try {
            final StoredDisplayAnimations stored = NametagPdcYamlCodec.decode(yaml, StoredDisplayAnimations.class);
            if (stored == null || stored.animations == null || stored.animations.isEmpty()) {
                return Map.of();
            }
            final Map<Integer, DisplayAnimation> result = new LinkedHashMap<>();
            stored.animations.forEach((key, value) -> {
                try {
                    result.put(Integer.parseInt(key), value);
                } catch (NumberFormatException ignored) {
                    logger.warning("Invalid display animation index in PDC for " + player.getName() + ": " + key);
                }
            });
            return Map.copyOf(result);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read display animations from PDC for " + player.getName(), e);
            return Map.of();
        }
    }

    public void writeDisplayAnimations(@NotNull Player player, @NotNull Map<Integer, DisplayAnimation> animations) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (animations.isEmpty()) {
            pdc.remove(displayAnimationsKey);
            return;
        }
        final StoredDisplayAnimations stored = new StoredDisplayAnimations();
        animations.forEach((index, anim) -> stored.animations.put(String.valueOf(index), anim));
        try {
            pdc.set(displayAnimationsKey, PersistentDataType.STRING,
                    NametagPdcYamlCodec.encode(stored, StoredDisplayAnimations.class));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write display animations to PDC for " + player.getName(), e);
        }
    }

    public void clearDisplayAnimation(@NotNull Player player, int groupIndex) {
        final Map<Integer, DisplayAnimation> current = new LinkedHashMap<>(readDisplayAnimations(player));
        if (current.remove(groupIndex) != null) {
            writeDisplayAnimations(player, current);
        }
    }

    @Configuration
    @NoArgsConstructor
    static final class StoredNametagOverride {
        @Nullable
        private Settings.NameTag nameTag;
    }

    @Configuration
    @Getter
    @NoArgsConstructor
    static final class StoredDisplayAnimations {
        private Map<String, DisplayAnimation> animations = new LinkedHashMap<>();
    }
}

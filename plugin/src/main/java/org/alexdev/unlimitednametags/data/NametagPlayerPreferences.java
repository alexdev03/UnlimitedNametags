package org.alexdev.unlimitednametags.data;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent nametag UI preferences stored on the player ({@link Player#getPersistentDataContainer()}).
 */
public final class NametagPlayerPreferences {

    private final NamespacedKey seeOthersKey;
    private final NamespacedKey showOwnSelfKey;
    private final NamespacedKey showOwnToOthersKey;

    public NametagPlayerPreferences(@NotNull org.bukkit.plugin.Plugin plugin) {
        this.seeOthersKey = new NamespacedKey(plugin, "pref_see_others");
        this.showOwnSelfKey = new NamespacedKey(plugin, "pref_show_own_self");
        this.showOwnToOthersKey = new NamespacedKey(plugin, "pref_show_own_to_others");
    }

    public boolean readSeeOthers(@NotNull Player player) {
        return readBoolean(player.getPersistentDataContainer(), seeOthersKey, true);
    }

    public boolean readShowOwnSelf(@NotNull Player player) {
        return readBoolean(player.getPersistentDataContainer(), showOwnSelfKey, true);
    }

    public boolean readShowOwnToOthers(@NotNull Player player) {
        return readBoolean(player.getPersistentDataContainer(), showOwnToOthersKey, true);
    }

    public void writeSeeOthers(@NotNull Player player, boolean seeOthers) {
        writeBoolean(player.getPersistentDataContainer(), seeOthersKey, seeOthers);
    }

    public void writeShowOwnSelf(@NotNull Player player, boolean show) {
        writeBoolean(player.getPersistentDataContainer(), showOwnSelfKey, show);
    }

    public void writeShowOwnToOthers(@NotNull Player player, boolean show) {
        writeBoolean(player.getPersistentDataContainer(), showOwnToOthersKey, show);
    }

    private static boolean readBoolean(@NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key,
            boolean defaultValue) {
        final Boolean v = pdc.get(key, PersistentDataType.BOOLEAN);
        return v != null ? v : defaultValue;
    }

    private static void writeBoolean(@NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key, boolean value) {
        pdc.set(key, PersistentDataType.BOOLEAN, value);
    }
}

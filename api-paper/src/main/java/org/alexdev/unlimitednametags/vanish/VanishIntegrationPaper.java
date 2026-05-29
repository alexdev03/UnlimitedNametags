package org.alexdev.unlimitednametags.vanish;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface VanishIntegrationPaper extends VanishIntegration {

    default boolean canSee(@NotNull Player viewer, @NotNull Player other) {
        return canSee(viewer.getUniqueId(), other.getUniqueId());
    }

    default boolean isVanished(@NotNull Player player) {
        return isVanished(player.getUniqueId());
    }
}

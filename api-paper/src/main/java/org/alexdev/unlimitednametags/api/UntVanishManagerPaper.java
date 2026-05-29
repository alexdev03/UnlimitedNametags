package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntVanishManagerPaper extends UntVanishManager {

    default boolean canSee(@NotNull Player viewer, @NotNull Player other) {
        return canSee(viewer.getUniqueId(), other.getUniqueId());
    }

    default boolean isVanished(@NotNull Player player) {
        return isVanished(player.getUniqueId());
    }

    default void vanishPlayer(@NotNull Player player) {
        vanishPlayer(player.getUniqueId());
    }

    default void unVanishPlayer(@NotNull Player player) {
        unVanishPlayer(player.getUniqueId());
    }
}

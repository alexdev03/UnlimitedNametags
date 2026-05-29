package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paper/Bukkit {@link UntNametagDisplayCore} with {@link Player} convenience overloads.
 */
public interface UntNametagDisplay extends UntNametagDisplayCore {

    default void refreshForPlayer(@NotNull Player player) {
        refreshForViewer(player.getUniqueId());
    }

    default void showToPlayer(@NotNull Player player) {
        showToViewer(player.getUniqueId());
    }

    default void hideFromPlayer(@NotNull Player player) {
        hideFromViewer(player.getUniqueId());
    }

    default void showToPlayers(@NotNull Set<Player> players) {
        showToViewers(players.stream().map(Player::getUniqueId).collect(Collectors.toSet()));
    }

    default void setForcedNameTag(@NotNull Player viewer, @NotNull net.kyori.adventure.text.Component component) {
        setForcedNameTag(viewer.getUniqueId(), component);
    }

    default void clearForcedNameTag(@NotNull Player viewer) {
        clearForcedNameTag(viewer.getUniqueId());
    }
}

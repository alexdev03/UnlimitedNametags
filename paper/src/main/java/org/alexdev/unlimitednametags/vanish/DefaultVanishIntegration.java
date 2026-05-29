package org.alexdev.unlimitednametags.vanish;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class DefaultVanishIntegration implements VanishIntegration {

    @Override
    public boolean canSee(@NotNull UUID viewerId, @NotNull UUID otherId) {
        final Player viewer = Bukkit.getPlayer(viewerId);
        final Player other = Bukkit.getPlayer(otherId);
        if (viewer == null || other == null) {
            return false;
        }
        return viewer.canSee(other);
    }

    @Override
    public boolean isVanished(@NotNull UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        return player != null && player.hasMetadata("vanished");
    }

}

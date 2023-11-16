package org.alexdev.unlimitednametags.vanish;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class DefaultVanishIntegration implements VanishIntegration {

    @Override
    public boolean canSee(@NotNull Player name, @NotNull Player other) {
        return true;
    }

    @Override
    public boolean isVanished(@NotNull Player name) {
        return false;
    }

}

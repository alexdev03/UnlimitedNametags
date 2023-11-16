package org.alexdev.unlimitednametags.vanish;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface VanishIntegration {

    boolean canSee(@NotNull Player name, @NotNull Player other);

    boolean isVanished(@NotNull Player name);

}

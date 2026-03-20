package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntVanishManager {

    void setIntegration(@NotNull VanishIntegration integration);

    @NotNull
    VanishIntegration getIntegration();

    boolean canSee(@NotNull Player name, @NotNull Player other);

    boolean isVanished(@NotNull Player name);

    void vanishPlayer(@NotNull Player player);

    void unVanishPlayer(@NotNull Player player);
}

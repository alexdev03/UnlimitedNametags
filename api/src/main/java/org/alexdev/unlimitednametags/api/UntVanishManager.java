package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface UntVanishManager {

    void setIntegration(@NotNull VanishIntegration integration);

    @NotNull
    VanishIntegration getIntegration();

    boolean canSee(@NotNull UUID viewerId, @NotNull UUID otherId);

    boolean isVanished(@NotNull UUID playerId);

    void vanishPlayer(@NotNull UUID playerId);

    void unVanishPlayer(@NotNull UUID playerId);
}

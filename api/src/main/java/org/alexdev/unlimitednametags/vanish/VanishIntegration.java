package org.alexdev.unlimitednametags.vanish;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface VanishIntegration {

    boolean canSee(@NotNull UUID viewerId, @NotNull UUID otherId);

    boolean isVanished(@NotNull UUID playerId);
}

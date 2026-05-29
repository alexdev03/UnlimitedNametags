package org.alexdev.unlimitednametags.hook.hat;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface HatHook {

    double getHigh(@NotNull UUID playerId);
}

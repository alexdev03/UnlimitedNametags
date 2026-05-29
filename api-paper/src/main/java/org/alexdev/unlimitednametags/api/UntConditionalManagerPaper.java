package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntConditionalManagerPaper extends UntConditionalManager {

    default boolean evaluateCondition(@NotNull String expression, @NotNull Player player) {
        return evaluateCondition(expression, player.getUniqueId());
    }

    default boolean evaluateCondition(@NotNull String expression, @NotNull Player viewer, @NotNull Player owner) {
        return evaluateCondition(expression, viewer.getUniqueId(), owner.getUniqueId());
    }
}

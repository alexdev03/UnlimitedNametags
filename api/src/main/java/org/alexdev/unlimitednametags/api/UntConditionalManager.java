package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntConditionalManager {

    /**
     * Evaluates a JEXL boolean expression (after PlaceholderAPI when enabled).
     */
    boolean evaluateCondition(@NotNull String expression, @NotNull Player player);
}

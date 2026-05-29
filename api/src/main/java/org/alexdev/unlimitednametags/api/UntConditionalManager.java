package org.alexdev.unlimitednametags.api;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface UntConditionalManager {

    /**
     * Evaluates a JEXL boolean expression (after placeholder expansion on the platform).
     */
    boolean evaluateCondition(@NotNull String expression, @NotNull UUID playerId);

    /**
     * Evaluates a JEXL boolean expression after relational placeholder expansion.
     */
    boolean evaluateCondition(@NotNull String expression, @NotNull UUID viewerId, @NotNull UUID ownerId);
}

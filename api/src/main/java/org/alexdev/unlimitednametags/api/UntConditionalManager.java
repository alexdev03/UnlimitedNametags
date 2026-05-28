package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntConditionalManager {

    /**
     * Evaluates a JEXL boolean expression (after PlaceholderAPI when enabled).
     */
    boolean evaluateCondition(@NotNull String expression, @NotNull Player player);

    /**
     * Evaluates a JEXL boolean expression after {@link me.clip.placeholderapi.PlaceholderAPI#setRelationalPlaceholders(Player, Player, String)}
     * with {@code viewer} as the target who sees and {@code owner} as the contextual player (nametag owner).
     */
    boolean evaluateCondition(@NotNull String expression, @NotNull Player viewer, @NotNull Player owner);
}

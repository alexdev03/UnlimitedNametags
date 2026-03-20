package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface UntConditionalManager {

    boolean evaluateExpression(@NotNull Settings.ConditionalModifier modifier, @NotNull Player player);
}

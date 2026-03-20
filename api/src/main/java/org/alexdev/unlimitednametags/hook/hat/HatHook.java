package org.alexdev.unlimitednametags.hook.hat;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface HatHook {

    /**
     * Get the height of the player's hat
     * @param player the player involved
     * @return the height of the player's hat
     */
    double getHigh(@NotNull Player player);

}

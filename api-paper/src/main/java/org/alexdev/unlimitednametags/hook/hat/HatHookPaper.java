package org.alexdev.unlimitednametags.hook.hat;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface HatHookPaper extends HatHook {

    default double getHigh(@NotNull Player player) {
        return getHigh(player.getUniqueId());
    }
}

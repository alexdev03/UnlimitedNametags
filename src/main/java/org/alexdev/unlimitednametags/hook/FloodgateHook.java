package org.alexdev.unlimitednametags.hook;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

public class FloodgateHook {

    private FloodgateApi floodgateApi;

    public FloodgateHook() {
        this.floodgateApi = FloodgateApi.getInstance();
    }

    public boolean isBedrock(@NotNull Player player) {
        return floodgateApi.isFloodgatePlayer(player.getUniqueId());
    }

}

package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

public class FloodgateHook extends Hook {

    private FloodgateApi floodgateApi;

    public FloodgateHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    public boolean isBedrock(@NotNull Player player) {
        return floodgateApi.isFloodgatePlayer(player.getUniqueId());
    }

    @Override
    public void onEnable() {
        this.floodgateApi = FloodgateApi.getInstance();
    }

    @Override
    public void onDisable() {

    }
}

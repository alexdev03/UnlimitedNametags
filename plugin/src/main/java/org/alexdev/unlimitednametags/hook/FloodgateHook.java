package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

public class FloodgateHook extends Hook {

    private FloodgateApi floodgateAPI;

    public FloodgateHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        floodgateAPI = FloodgateApi.getInstance();
    }

    public boolean isFloodgatePlayer(@NotNull Player player) {
        return floodgateAPI.isFloodgatePlayer(player.getUniqueId());
    }

    @Override
    public void onDisable() {

    }
}

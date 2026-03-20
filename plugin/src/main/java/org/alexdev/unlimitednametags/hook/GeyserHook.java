package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.geysermc.geyser.api.GeyserApi;

public class GeyserHook extends Hook {

    private GeyserApi geyser;

    public GeyserHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        geyser = GeyserApi.api();
    }

    @Override
    public void onDisable() {
    }

    public boolean isBedrockPlayer(Player player) {
        return geyser.isBedrockPlayer(player.getUniqueId());
    }
}

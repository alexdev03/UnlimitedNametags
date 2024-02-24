package org.alexdev.unlimitednametags.placeholders;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class PAPIManager {

    private final UnlimitedNameTags plugin;

    public boolean isPAPIEnabled() {
        return plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @NotNull
    public String setPlaceholders(Player player, String text) {
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }

}

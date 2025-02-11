package org.alexdev.unlimitednametags.placeholders;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.PlaceholderAPI;
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
        if (text.isEmpty()) {
            return text;
        }
        try {
            final String firstReplacement = PlaceholderAPI.setPlaceholders(player, text);
            return PlaceholderAPI.setPlaceholders(player, firstReplacement);
        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to set placeholders for text: " + text, e);
            return text;
        }
    }

    @NotNull
    public String setRelationalPlaceholders(@NotNull Player whoSees, @NotNull Player target, @NotNull String text) {
        if (text.isEmpty()) {
            return text;
        }
        try {
            final String firstReplacement = PlaceholderAPI.setRelationalPlaceholders(whoSees, target, text);
            return PlaceholderAPI.setRelationalPlaceholders(whoSees, target, firstReplacement);
        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to set relational placeholders for text: " + text, e);
            return text;
        }
    }

}

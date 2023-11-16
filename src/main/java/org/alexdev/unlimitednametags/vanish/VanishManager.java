package org.alexdev.unlimitednametags.vanish;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VanishManager {

    private final UnlimitedNameTags plugin;
    private VanishIntegration integration;

    public VanishManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        setIntegration(new DefaultVanishIntegration());
    }

    public void setIntegration(@NotNull VanishIntegration integration) {
        this.integration = integration;
    }

    @NotNull
    public VanishIntegration getIntegration() {
        return integration;
    }

    public boolean canSee(@NotNull Player name, @NotNull Player other) {
        return integration.canSee(name, other);
    }

    public boolean isVanished(@NotNull Player name) {
        return integration.isVanished(name);
    }

    public void vanishPlayer(@NotNull Player player) {
        plugin.getNametagManager().vanishPlayer(player);
    }

    public void unVanishPlayer(@NotNull Player player) {
        plugin.getNametagManager().unVanishPlayer(player);
    }
}

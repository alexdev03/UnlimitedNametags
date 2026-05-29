package org.alexdev.unlimitednametags.vanish;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.UntVanishManagerPaper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VanishManager implements UntVanishManagerPaper {

    private final UnlimitedNameTags plugin;
    private VanishIntegration integration;

    public VanishManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        setIntegration(new DefaultVanishIntegration());
    }

    @Override
    public void setIntegration(@NotNull VanishIntegration integration) {
        this.integration = integration;
    }

    @Override
    @NotNull
    public VanishIntegration getIntegration() {
        return integration;
    }

    @Override
    public boolean canSee(@NotNull UUID viewerId, @NotNull UUID otherId) {
        return integration.canSee(viewerId, otherId);
    }

    @Override
    public boolean isVanished(@NotNull UUID playerId) {
        return integration.isVanished(playerId);
    }

    @Override
    public void vanishPlayer(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player != null) {
            plugin.getNametagManager().vanishPlayer(player);
        }
    }

    @Override
    public void unVanishPlayer(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player != null) {
            plugin.getNametagManager().unVanishPlayer(player);
        }
    }
}

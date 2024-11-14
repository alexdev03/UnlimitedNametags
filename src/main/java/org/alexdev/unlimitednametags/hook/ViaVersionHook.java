package org.alexdev.unlimitednametags.hook;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ViaVersionHook extends Hook {

    private ViaAPI<Player> viaAPI;

    public ViaVersionHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        viaAPI = Via.getAPI();
    }

    @Override
    public void onDisable() {

    }

    public boolean hasNotTextDisplays(@NotNull Player player) {
        return viaAPI.getPlayerVersion(player) < 762;
    }
}

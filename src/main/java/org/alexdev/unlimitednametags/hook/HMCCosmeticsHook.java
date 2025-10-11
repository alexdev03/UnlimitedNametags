package org.alexdev.unlimitednametags.hook;

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HMCCosmeticsHook extends Hook {


    public HMCCosmeticsHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public boolean hasBackpack(@NotNull Player player) {
        final CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        return user != null && user.hasCosmeticInSlot(CosmeticSlot.BACKPACK);
    }
}

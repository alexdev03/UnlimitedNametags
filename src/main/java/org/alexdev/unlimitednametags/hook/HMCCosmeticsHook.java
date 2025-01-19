package org.alexdev.unlimitednametags.hook;

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HMCCosmeticsHook extends Hook implements HatHook {


    public HMCCosmeticsHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }


    @Override
    public double getHigh(@NotNull Player player) {
        final CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (user == null) {
            return 0;
        }

        final Cosmetic cosmetic = user.getCosmetic(CosmeticSlot.HELMET);
        if (cosmetic == null) {
            return 0;
        }

        return 0;
    }
}

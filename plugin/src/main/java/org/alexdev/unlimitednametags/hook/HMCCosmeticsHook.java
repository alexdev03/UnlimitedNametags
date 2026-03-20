package org.alexdev.unlimitednametags.hook;

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class HMCCosmeticsHook extends Hook implements HatHook {

    private CreativeHook creativeHook;

    public HMCCosmeticsHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getTaskScheduler().runTaskLater(() -> {
            creativeHook = plugin.getHooks().values()
                    .stream()
                    .filter(hook -> hook instanceof CreativeHook)
                    .map(hook -> (CreativeHook) hook)
                    .findFirst()
                    .orElse(null);
        }, 1);
    }

    @Override
    public void onDisable() {
    }

    public boolean hasBackpack(@NotNull Player player) {
        final CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        return user != null && user.hasCosmeticInSlot(CosmeticSlot.BACKPACK);
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

        final ItemStack item = cosmetic.getItem();
        if (item == null) {
            return 0;
        }

        if (creativeHook != null) {
            return creativeHook.getHigh(item);
        }

        return 0;
    }
}

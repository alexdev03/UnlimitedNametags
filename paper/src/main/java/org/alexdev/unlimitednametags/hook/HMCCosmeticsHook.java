package org.alexdev.unlimitednametags.hook;

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.hat.HatHookPaper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HMCCosmeticsHook extends Hook implements HatHookPaper {

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
    public double getHigh(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        return getHatItems(player).stream()
                .mapToDouble(item -> getHigh(player, item))
                .max()
                .orElse(0);
    }

    @Override
    public @NotNull List<ItemStack> getHatItems(@NotNull Player player) {
        final CosmeticUser user = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (user == null) {
            return List.of();
        }

        if (!user.hasCosmeticInSlot(CosmeticSlot.HELMET)) {
            return List.of();
        }

        final ItemStack item = user.getUserCosmeticItem(CosmeticSlot.HELMET);
        if (item == null || item.getType().isAir()) {
            return List.of();
        }

        return List.of(item);
    }

    @Override
    public double getHigh(@NotNull Player player, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        if (creativeHook != null) {
            return creativeHook.getHigh(item);
        }

        return 0;
    }
}

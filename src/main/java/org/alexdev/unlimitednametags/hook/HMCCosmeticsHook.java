package org.alexdev.unlimitednametags.hook;

import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class HMCCosmeticsHook extends Hook implements Listener, HatHook {

    private CreativeHook creativeHook;
    private boolean enabled = false;

    public HMCCosmeticsHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        loadCreativeHook();
    }

    public double getHigh(@NotNull Player player) {
        if (!enabled) {
            return 0;
        }

        final CosmeticUser cosmeticUser = HMCCosmeticsAPI.getUser(player.getUniqueId());
        if (cosmeticUser == null) {
            return 0;
        }

        final Cosmetic cosmetic = cosmeticUser.getCosmetic(CosmeticSlot.HELMET);
        if (cosmetic == null) {
            return 0;
        }

        final ItemStack item = cosmetic.getItem();
        if (item == null) {
            return 0;
        }

        return creativeHook.getHigh(item);
    }

    private void loadCreativeHook() {
        this.creativeHook = plugin.getHooks().values().stream()
                .filter(h -> h instanceof CreativeHook)
                .map(h -> (CreativeHook) h)
                .findFirst()
                .orElse(null);

        if (creativeHook == null) {
            plugin.getLogger().warning("CreativeHook not found, cannot support HMCCosmetics");
            return;
        }

        enabled = true;
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
    }

}

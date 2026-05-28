package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Console snapshots for {@code advanced.yml} helmet rule troubleshooting.
 */
public final class HelmetRuleDiagnostics {

    private HelmetRuleDiagnostics() {
    }

    public static void logEquippedHelmet(@NotNull final UnlimitedNameTags plugin, @NotNull final Player player) {
        final ItemStack helmet = player.getInventory().getHelmet();
        final StringBuilder sb = new StringBuilder(256);
        sb.append("[UNT helmet dbg] === helmet snapshot player=").append(player.getName())
                .append(" uuid=").append(player.getUniqueId())
                .append(" world=").append(player.getWorld().getName());
        if (helmet == null || helmet.getType().isAir()) {
            sb.append(" helmet=<empty>");
            plugin.getLogger().info(sb.toString());
            return;
        }
        sb.append(" type=").append(helmet.getType().getKey())
                .append(" amount=").append(helmet.getAmount());
        final ItemMeta meta = helmet.getItemMeta();
        if (meta == null) {
            sb.append(" meta=<null>");
            plugin.getLogger().info(sb.toString());
            return;
        }
        sb.append(" cmd=");
        if (meta.hasCustomModelData()) {
            sb.append(meta.getCustomModelData());
        } else {
            sb.append("<none>");
        }
        sb.append(" hasEquippable=").append(meta.hasEquippable());
        if (meta.hasEquippable()) {
            sb.append(" equippable.model=").append(meta.getEquippable().getModel());
        }
        sb.append(" hasItemModel=").append(meta.hasItemModel());
        if (meta.hasItemModel()) {
            sb.append(" itemModel=").append(meta.getItemModel());
        }
        plugin.getLogger().info(sb.toString());
    }
}

package org.alexdev.unlimitednametags.hook.hat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface HatHookPaper extends HatHook {

    default double getHigh(@NotNull Player player) {
        return getHigh(player.getUniqueId());
    }

    /**
     * Returns additional Paper-side hat item sources exposed by this hook, for example virtual cosmetics that are not
     * present in the vanilla helmet equipment slot. Implementations should not include empty/air items.
     */
    @NotNull
    default List<ItemStack> getHatItems(@NotNull Player player) {
        return List.of();
    }

    /**
     * Computes the raw hat height for the supplied item source. This lets item/model based hooks evaluate both the
     * real helmet item and virtual cosmetic items supplied by other hooks without requiring the item to be equipped.
     */
    default double getHigh(@NotNull Player player, @Nullable ItemStack item) {
        return 0;
    }
}

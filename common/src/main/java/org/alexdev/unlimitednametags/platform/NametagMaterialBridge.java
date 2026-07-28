package org.alexdev.unlimitednametags.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves item/block display materials from config strings without Bukkit types in common.
 */
public interface NametagMaterialBridge {

    @Nullable
    Object resolveItemStack(@NotNull java.util.UUID ownerId, @NotNull String materialKey);

    @Nullable
    default Object resolveItemStack(@NotNull java.util.UUID ownerId, @NotNull String materialKey,
            @Nullable Integer customModelData, @Nullable String itemModel, @Nullable String nexoId) {
        return resolveItemStack(ownerId, materialKey);
    }

    @Nullable
    Object resolveBlockState(@NotNull java.util.UUID ownerId, @NotNull String materialKey);
}

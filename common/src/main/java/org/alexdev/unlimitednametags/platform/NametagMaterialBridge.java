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
    Object resolveBlockState(@NotNull java.util.UUID ownerId, @NotNull String materialKey);
}

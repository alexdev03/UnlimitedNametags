package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class BukkitNametagMaterialBridge implements NametagMaterialBridge {

    private final UnlimitedNameTags plugin;

    public BukkitNametagMaterialBridge(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable Object resolveItemStack(@NotNull UUID ownerId, @NotNull String materialKey) {
        final String expanded = expand(ownerId, materialKey);

        final org.bukkit.inventory.ItemStack itemStack = resolveItemFromRegistry(expanded);
        if (itemStack != null) {
            return SpigotConversionUtil.fromBukkitItemStack(itemStack);
        }

        final Material material = Material.matchMaterial(expanded, false);
        if (material == null || !material.isItem()) {
            return null;
        }
        return SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(material));
    }

    @Override
    public @Nullable Object resolveBlockState(@NotNull UUID ownerId, @NotNull String materialKey) {
        final String expanded = expand(ownerId, materialKey);

        final org.bukkit.block.data.BlockData data = resolveBlockFromRegistry(expanded);
        if (data != null) {
            return SpigotConversionUtil.fromBukkitBlockData(data);
        }

        final Material material = Material.matchMaterial(expanded, false);
        if (material == null || !material.isBlock()) {
            return null;
        }
        return SpigotConversionUtil.fromBukkitBlockData(material.createBlockData());
    }

    public static org.bukkit.inventory.ItemStack resolveItemFromRegistry(String key) {
        try {
            final org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(key);
            if (namespacedKey != null) {
                final org.bukkit.inventory.ItemType itemType = org.bukkit.Registry.ITEM.get(namespacedKey);
                if (itemType != null) {
                    return itemType.createItemStack();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static org.bukkit.block.data.BlockData resolveBlockFromRegistry(String key) {
        try {
            final org.bukkit.NamespacedKey namespacedKey = org.bukkit.NamespacedKey.fromString(key);
            if (namespacedKey != null) {
                final org.bukkit.block.BlockType blockType = org.bukkit.Registry.BLOCK.get(namespacedKey);
                if (blockType != null) {
                    return blockType.createBlockData();
                }
            }
        } catch (Throwable ignored) {}

        try {
            return org.bukkit.Bukkit.createBlockData(key);
        } catch (Throwable ignored) {}
        return null;
    }

    @NotNull
    private String expand(@NotNull UUID ownerId, @NotNull String raw) {
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (owner == null) {
            return raw.trim();
        }
        return plugin.getPlaceholderManager().expandForOwner(owner, raw).trim();
    }

    public static ItemStack requireItemStack(@NotNull Object resolved) {
        return (ItemStack) resolved;
    }

    public static WrappedBlockState requireBlockState(@NotNull Object resolved) {
        return (WrappedBlockState) resolved;
    }

    public static String defaultMaterialKey(@Nullable String raw, @NotNull String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw;
    }

    public static Material matchOrNull(@NotNull String expanded) {
        return Material.matchMaterial(expanded.toUpperCase(Locale.ROOT), false);
    }
}

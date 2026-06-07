package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Advanced;
import org.alexdev.unlimitednametags.hook.hat.HatHookPaper;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AdvancedHatHook implements HatHookPaper {

    private final UnlimitedNameTags plugin;

    public AdvancedHatHook(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public double getHigh(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return 0;
        }

        final ItemStack helmet = player.getInventory().getHelmet();
        return getHigh(player, helmet);
    }

    @Override
    public double getHigh(@NotNull Player player, @Nullable ItemStack helmet) {
        final Advanced advanced = plugin.getConfigManager().getAdvanced();
        final List<Advanced.HelmetHeightRule> rules = advanced.getHelmetHeightRules();
        final boolean verbose = HelmetDebugContext.isVerbose();

        if (rules == null || rules.isEmpty()) {
            if (verbose) {
                plugin.getLogger().info("[UNT helmet dbg] AdvancedHatHook: no helmetHeightRules loaded");
            }
            return 0;
        }

        if (helmet == null || helmet.getType().isAir()) {
            if (verbose) {
                plugin.getLogger().info("[UNT helmet dbg] AdvancedHatHook: helmet slot empty");
            }
            return 0;
        }

        if (verbose) {
            plugin.getLogger().info("[UNT helmet dbg] AdvancedHatHook: evaluating " + rules.size() + " rule(s)");
        }

        int index = 0;
        for (final Advanced.HelmetHeightRule rule : rules) {
            if (rule.getHeight() <= 0) {
                if (verbose) {
                    plugin.getLogger().info("[UNT helmet dbg] rule[" + index + "] priority=" + rule.getPriority()
                            + " SKIP height<=0");
                }
                index++;
                continue;
            }
            if (!rule.definesItemMatch()) {
                if (verbose) {
                    plugin.getLogger().info("[UNT helmet dbg] rule[" + index + "] priority=" + rule.getPriority()
                            + " SKIP no item matcher (material/equippableModel/itemModel/cmd)");
                }
                index++;
                continue;
            }

            final Optional<String> failure = verifyRule(rule, player, helmet);
            if (failure.isPresent()) {
                if (verbose) {
                    plugin.getLogger().info("[UNT helmet dbg] rule[" + index + "] priority=" + rule.getPriority()
                            + " NO MATCH: " + failure.get());
                }
                index++;
                continue;
            }

            if (verbose) {
                plugin.getLogger().info("[UNT helmet dbg] rule[" + index + "] priority=" + rule.getPriority()
                        + " MATCH -> raw height=" + rule.getHeight());
            }
            return rule.getHeight();
        }

        if (verbose) {
            plugin.getLogger().info("[UNT helmet dbg] AdvancedHatHook: no rule matched");
        }
        return 0;
    }

    private @NotNull Optional<String> verifyRule(
            @NotNull Advanced.HelmetHeightRule rule,
            @NotNull Player player,
            @NotNull ItemStack helmet) {

        if (rule.getPermission() != null && !rule.getPermission().isEmpty() && !player.hasPermission(rule.getPermission())) {
            return Optional.of("permission '" + rule.getPermission() + "'");
        }

        if (rule.getWorlds() != null && !rule.getWorlds().isEmpty()
                && !rule.getWorlds().contains(player.getWorld().getName())) {
            return Optional.of("world not in " + rule.getWorlds());
        }

        if (rule.getMaterial() != null && !rule.getMaterial().isEmpty()) {
            final Material mat = Material.matchMaterial(rule.getMaterial(), false);
            if (mat == null || helmet.getType() != mat) {
                return Optional.of("material want '" + rule.getMaterial() + "' have '" + helmet.getType() + "'");
            }
        }

        if (rule.getEquippableModel() != null && !rule.getEquippableModel().isEmpty()) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasEquippable()) {
                return Optional.of("equippableModel rule but helmet hasEquippable=false");
            }
            final NamespacedKey modelKey = meta.getEquippable().getModel();
            if (modelKey == null) {
                if (meta.hasItemModel() && meta.getItemModel() != null) {
                    return Optional.of("equippable.model is null but item has item_model=" + meta.getItemModel()
                            + " - use itemModel in advanced.yml, not equippableModel");
                }
                return Optional.of("equippable.model is null");
            }
            final NamespacedKey wanted = NamespacedKey.fromString(rule.getEquippableModel());
            if (wanted == null) {
                return Optional.of("equippableModel invalid key string '" + rule.getEquippableModel() + "'");
            }
            if (!wanted.equals(modelKey)) {
                return Optional.of("equippable.model want " + wanted + " have " + modelKey);
            }
        }

        if (rule.getItemModel() != null && !rule.getItemModel().isEmpty()) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasItemModel()) {
                return Optional.of("itemModel rule but helmet hasItemModel=false");
            }
            final NamespacedKey modelKey = meta.getItemModel();
            if (modelKey == null) {
                return Optional.of("itemModel component null");
            }
            final NamespacedKey wanted = NamespacedKey.fromString(rule.getItemModel());
            if (wanted == null) {
                return Optional.of("itemModel invalid key string '" + rule.getItemModel() + "'");
            }
            if (!wanted.equals(modelKey)) {
                return Optional.of("item_model want " + wanted + " have " + modelKey);
            }
        }

        final boolean wantsRange = rule.getCustomModelDataMin() != null && rule.getCustomModelDataMax() != null;
        if (wantsRange) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) {
                return Optional.of("CMD range rule but helmet hasCustomModelData=false");
            }

            final int cmd = meta.getCustomModelData();
            if (cmd < rule.getCustomModelDataMin() || cmd > rule.getCustomModelDataMax()) {
                return Optional.of("CMD " + cmd + " not in [" + rule.getCustomModelDataMin() + "," + rule.getCustomModelDataMax() + "]");
            }
        } else if (rule.getCustomModelData() != null) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) {
                return Optional.of("CMD rule but helmet hasCustomModelData=false");
            }

            if (meta.getCustomModelData() != rule.getCustomModelData()) {
                return Optional.of("CMD want " + rule.getCustomModelData() + " have " + meta.getCustomModelData());
            }
        }

        return Optional.empty();
    }
}

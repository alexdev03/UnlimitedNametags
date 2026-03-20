package org.alexdev.unlimitednametags.hook;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Advanced;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public final class AdvancedHatHook implements HatHook {

    private final UnlimitedNameTags plugin;

    public AdvancedHatHook(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public double getHigh(@NotNull Player player) {
        final List<Advanced.HelmetHeightRule> rules = plugin.getConfigManager().getAdvanced().getHelmetHeightRules();
        if (rules == null || rules.isEmpty()) {
            return 0;
        }

        final ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir()) {
            return 0;
        }

        return rules.stream()
                .sorted(Comparator.comparingInt(Advanced.HelmetHeightRule::getPriority).reversed())
                .filter(rule -> rule.getHeight() > 0)
                .filter(Advanced.HelmetHeightRule::definesItemMatch)
                .filter(rule -> matches(rule, player, helmet))
                .mapToDouble(Advanced.HelmetHeightRule::getHeight)
                .findFirst()
                .orElse(0);
    }

    private boolean matches(@NotNull Advanced.HelmetHeightRule rule, @NotNull Player player, @NotNull ItemStack helmet) {
        if (rule.getPermission() != null && !rule.getPermission().isEmpty() && !player.hasPermission(rule.getPermission())) {
            return false;
        }

        if (rule.getWorlds() != null && !rule.getWorlds().isEmpty()
                && !rule.getWorlds().contains(player.getWorld().getName())) {
            return false;
        }

        if (rule.getMaterial() != null && !rule.getMaterial().isEmpty()) {
            final Material mat = Material.matchMaterial(rule.getMaterial(), false);
            if (mat == null || helmet.getType() != mat) {
                return false;
            }
        }

        if (rule.getEquippableModel() != null && !rule.getEquippableModel().isEmpty()) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasEquippable()) {
                return false;
            }
            final NamespacedKey modelKey = meta.getEquippable().getModel();
            if (modelKey == null) {
                return false;
            }
            final NamespacedKey wanted = NamespacedKey.fromString(rule.getEquippableModel());
            if (wanted == null || !wanted.equals(modelKey)) {
                return false;
            }
        }

        final boolean wantsRange = rule.getCustomModelDataMin() != null && rule.getCustomModelDataMax() != null;
        if (wantsRange) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) {
                return false;
            }

            final int cmd = meta.getCustomModelData();
            return cmd >= rule.getCustomModelDataMin() && cmd <= rule.getCustomModelDataMax();
        } else if (rule.getCustomModelData() != null) {
            final ItemMeta meta = helmet.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) {
                return false;
            }

            return meta.getCustomModelData() == rule.getCustomModelData();
        }

        return true;
    }
}

package org.alexdev.unlimitednametags.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

final class AdvancedConfigValidator {

  private AdvancedConfigValidator() {
  }

  static void validate(@NotNull Advanced advanced, @NotNull Consumer<String> warning) {
    final List<Advanced.HelmetHeightRule> rules = advanced.getHelmetHeightRules();
    if (rules == null) {
      return;
    }
    for (int i = 0; i < rules.size(); i++) {
      final Advanced.HelmetHeightRule rule = rules.get(i);
      if (!rule.definesItemMatch()) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] has no item matcher; it will never apply.");
      }
      if (rule.getHeight() <= 0) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] height must be > 0; it will never apply.");
      }
      final boolean minSet = rule.getCustomModelDataMin() != null;
      final boolean maxSet = rule.getCustomModelDataMax() != null;
      if (minSet != maxSet) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] must set both customModelDataMin and customModelDataMax for a CMD range.");
      }
      if (minSet && maxSet && rule.getCustomModelData() != null) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] customModelData is ignored when customModelDataMin/Max range is set.");
      }
      if (rule.getMaterial() != null && !rule.getMaterial().isEmpty()
          && Material.matchMaterial(rule.getMaterial(), false) == null) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] unknown material: " + rule.getMaterial());
      }
      if (rule.getEquippableModel() != null && !rule.getEquippableModel().isEmpty()
          && NamespacedKey.fromString(rule.getEquippableModel()) == null) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] equippableModel is not a valid resource location: "
            + rule.getEquippableModel());
      }
      if (rule.getItemModel() != null && !rule.getItemModel().isEmpty()
          && NamespacedKey.fromString(rule.getItemModel()) == null) {
        warning.accept("advanced.yml helmetHeightRules[" + i + "] itemModel is not a valid resource location: "
            + rule.getItemModel());
      }
    }
  }
}

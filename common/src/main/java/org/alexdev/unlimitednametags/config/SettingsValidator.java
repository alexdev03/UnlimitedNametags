package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SettingsValidator {

  private SettingsValidator() {
  }

  public static boolean isValidBackgroundColor(@Nullable String color) {
    return ColorStrings.isValid(color);
  }

  /**
   * Validates loaded settings and applies in-memory fixes. Returns whether settings were mutated.
   */
  public static boolean validateAndFix(
      @NotNull Settings settings,
      @NotNull Consumer<String> warning) {
    if (settings.getConfigVersion() != SettingsConfigVersion.CURRENT) {
      warning.accept("settings.yml configVersion is " + settings.getConfigVersion()
          + " but this build expects " + SettingsConfigVersion.CURRENT
          + "; reload after backup or migrate the file.");
    }

    if (settings.getDefaultNameTag().isEmpty()) {
      throw new IllegalStateException("Default name tag is empty");
    }

    Map<String, Settings.NameTag> nameTagFixes = null;
    boolean save = false;

    if (settings.getVisibility().getThroughWallSettings().getCheckInterval() < 1) {
      warning.accept("throughWallCheckInterval must be >= 1; resetting to 1.");
      settings.getVisibility().getThroughWallSettings().setCheckInterval(1);
      save = true;
    }
    if (settings.getVisibility().getThroughWallSettings().getMaxDistance() <= 0.0) {
      warning.accept("throughWallMaxDistance must be > 0; resetting to 48.");
      settings.getVisibility().getThroughWallSettings().setMaxDistance(48.0);
      save = true;
    }

    for (final Map.Entry<String, Settings.NameTag> entry : settings.getNameTags().entrySet()) {
      final Settings.NameTag nameTag = entry.getValue();
      boolean entryFixed = false;
      final ArrayList<Settings.DisplayGroup> fixedGroups = new ArrayList<>(nameTag.displayGroups().size());
      for (final Settings.DisplayGroup group : nameTag.displayGroups()) {
        Settings.DisplayGroup fixed = group;
        if (group.scale() <= 0f) {
          warning.accept("NameTag '" + entry.getKey() + "': display group scale is <= 0; persisted as 1.0.");
          fixed = fixed.withScale(1f);
          entryFixed = true;
        }
        if (group.background() != null) {
          final String color = group.background().color();
          if (!isValidBackgroundColor(color)) {
            warning.accept("NameTag '" + entry.getKey() + "': background color '" + color
                + "' is invalid; expected #RRGGBB or R,G,B. Will render as transparent.");
          }
        }
        fixedGroups.add(fixed);
      }
      if (entryFixed) {
        if (nameTagFixes == null) {
          nameTagFixes = new LinkedHashMap<>();
        }
        nameTagFixes.put(entry.getKey(), new Settings.NameTag(nameTag.permission(), List.copyOf(fixedGroups)));
        save = true;
      }
    }

    if (nameTagFixes != null) {
      settings.getNameTags().putAll(nameTagFixes);
    }

    for (final Map.Entry<String, GlowOverride> entry : settings.getGlowAnimations().entrySet()) {
      validateGlowOverride(entry.getKey(), entry.getValue(), settings.getGlowAnimations().keySet(), warning);
    }

    for (final Map.Entry<String, Settings.NameTag> entry : settings.getNameTags().entrySet()) {
      int groupIndex = 0;
      for (final Settings.DisplayGroup group : entry.getValue().displayGroups()) {
        if (group.glow() != null) {
          validateGlowOverride(
              entry.getKey() + " displayGroups[" + groupIndex + "].glow",
              group.glow(),
              settings.getGlowAnimations().keySet(),
              warning);
        }
        groupIndex++;
      }
    }

    return save;
  }

  private static void validateGlowOverride(
      @NotNull String context,
      @NotNull GlowOverride glow,
      @NotNull java.util.Set<String> animationIds,
      @NotNull Consumer<String> warning) {
    if (glow instanceof GlowOverride.FixedGlowOverride fixed) {
      if (!ColorStrings.isValid(fixed.color())) {
        warning.accept(context + ": fixed glow color '" + fixed.color() + "' is invalid; expected #RRGGBB or R,G,B.");
      }
    } else if (glow instanceof GlowOverride.ReferenceGlowOverride ref) {
      final String key = ref.ref();
      if (key == null || key.isBlank() || !animationIds.contains(key.trim())) {
        warning.accept(context + ": glow reference '" + key + "' does not exist in glowAnimations.");
      }
    } else if (glow instanceof GlowOverride.CustomGlowOverride custom) {
      final String handlerId = custom.id();
      if (handlerId == null || handlerId.isBlank()) {
        warning.accept(context + ": custom glow requires a non-blank id.");
      }
    } else if (glow instanceof GlowOverride.GradientGlowOverride gradient) {
      final var colors = gradient.colors();
      if (colors == null || colors.size() < 2) {
        warning.accept(context + ": gradient glow requires at least two colors.");
      } else {
        for (final String color : colors) {
          if (!ColorStrings.isValid(color)) {
            warning.accept(context + ": gradient color '" + color + "' is invalid.");
          }
        }
      }
      if (gradient.refreshInterval() < 1) {
        warning.accept(context + ": gradient refreshInterval must be >= 1.");
      }
    }
  }
}

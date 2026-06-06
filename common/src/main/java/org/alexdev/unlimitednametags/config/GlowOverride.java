package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Polymorphic;
import de.exlll.configlib.PolymorphicTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Optional per-display-group glow color override for text, item, and block displays.
 * YAML under {@code glow:} with {@code type: fixed | reference | rainbow | gradient | custom}.
 * Named presets live in {@link Settings#glowAnimations} and/or are registered via
 * {@code UNTPaperAPI.registerNametagGlowAnimation}, then referenced with {@code type: reference}.
 */
@Configuration
@Getter
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
@Polymorphic
@PolymorphicTypes({
        @PolymorphicTypes.Type(type = GlowOverride.FixedGlowOverride.class, alias = "fixed"),
        @PolymorphicTypes.Type(type = GlowOverride.ReferenceGlowOverride.class, alias = "reference"),
        @PolymorphicTypes.Type(type = GlowOverride.RainbowGlowOverride.class, alias = "rainbow"),
        @PolymorphicTypes.Type(type = GlowOverride.GradientGlowOverride.class, alias = "gradient"),
        @PolymorphicTypes.Type(type = GlowOverride.CustomGlowOverride.class, alias = "custom")
})
public abstract class GlowOverride {

    @Comment("If false, glow is disabled for this row.")
    private boolean enabled = true;

    @Comment("Tempo multiplier for animated glow types (rainbow, gradient, custom).")
    private double speed = 1.0;

    @Comment({
            "Optional string map for custom glow handlers (readable via API; built-in types ignore unknown keys).",
            "YAML example: customProperties: { mode: pulse }"
    })
    private Map<String, String> customProperties = new LinkedHashMap<>();

    public boolean isActive() {
        return enabled && speed > 0;
    }

    /**
     * Dereferences {@link ReferenceGlowOverride} against {@code settings.glowAnimations} and optional API presets.
     */
    @Nullable
    public GlowOverride resolve(@NotNull Settings settings) {
        return resolve(settings, null);
    }

    /**
     * @param externalLookup resolves preset ids not defined in config (e.g. {@code NametagRuntime#resolveGlowAnimation})
     */
    @Nullable
    public GlowOverride resolve(@NotNull Settings settings, @Nullable Function<String, GlowOverride> externalLookup) {
        if (this instanceof ReferenceGlowOverride ref) {
            final String key = ref.ref();
            if (key == null || key.isBlank()) {
                return null;
            }
            final String trimmed = key.trim();
            GlowOverride resolved = settings.getGlowAnimations().get(trimmed);
            if (resolved == null && externalLookup != null) {
                resolved = externalLookup.apply(trimmed);
            }
            if (resolved == null) {
                return null;
            }
            final GlowOverride inner = resolved.resolve(settings, externalLookup);
            if (inner == null) {
                return null;
            }
            final double refSpeed = ref.speed();
            if (refSpeed == 1.0) {
                return inner;
            }
            return NametagGlowOverrides.copyWithSpeed(inner, inner.speed() * refSpeed);
        }
        return this;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class FixedGlowOverride extends GlowOverride {

        @Comment("Hex (#RRGGBB) or RGB (R,G,B) glow color.")
        private String color = "#ffffff";
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class ReferenceGlowOverride extends GlowOverride {

        @Comment("Id of an entry in settings glowAnimations or a preset registered via UNTPaperAPI.registerNametagGlowAnimation.")
        private String ref = "";
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class RainbowGlowOverride extends GlowOverride {
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class GradientGlowOverride extends GlowOverride {

        @Comment("At least two hex or RGB color strings.")
        private List<String> colors = new ArrayList<>();

        @Comment("Ticks between color steps (still respects row glowInterval / displayAnimationInterval).")
        private int refreshInterval = 10;
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class CustomGlowOverride extends GlowOverride {

        @Comment("Must match a handler id passed to registerNametagCustomGlowHandler (UNTPaperAPI).")
        private String id = "";
    }
}

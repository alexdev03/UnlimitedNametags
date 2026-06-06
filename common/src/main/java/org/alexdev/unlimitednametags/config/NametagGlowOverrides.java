package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Factory helpers for {@link GlowOverride} subtypes (for use from code and the plugin API).
 */
public final class NametagGlowOverrides {

    private NametagGlowOverrides() {
    }

    @NotNull
    public static GlowOverride.FixedGlowOverride fixed(@NotNull String color) {
        final GlowOverride.FixedGlowOverride g = new GlowOverride.FixedGlowOverride();
        g.color(color);
        return g;
    }

    @NotNull
    public static GlowOverride.ReferenceGlowOverride reference(@NotNull String animationId) {
        return reference(animationId, 1.0);
    }

    @NotNull
    public static GlowOverride.ReferenceGlowOverride reference(@NotNull String animationId, double speed) {
        final GlowOverride.ReferenceGlowOverride g = new GlowOverride.ReferenceGlowOverride();
        g.ref(animationId);
        g.speed(speed);
        return g;
    }

    /**
     * Copies a resolved preset so {@code speed} can be changed without mutating config/API registry entries.
     */
    @NotNull
    public static GlowOverride copyWithSpeed(@NotNull GlowOverride source, double speed) {
        final GlowOverride copy;
        if (source instanceof GlowOverride.FixedGlowOverride fixed) {
            copy = fixed(fixed.color());
        } else if (source instanceof GlowOverride.RainbowGlowOverride) {
            copy = rainbow(speed);
        } else if (source instanceof GlowOverride.GradientGlowOverride gradient) {
            copy = gradient(gradient.colors(), gradient.refreshInterval());
        } else if (source instanceof GlowOverride.CustomGlowOverride custom) {
            copy = custom(custom.id());
        } else {
            return source;
        }
        copy.enabled(source.enabled());
        copy.speed(speed);
        if (source.customProperties() != null && !source.customProperties().isEmpty()) {
            copy.customProperties(new java.util.LinkedHashMap<>(source.customProperties()));
        }
        return copy;
    }

    @NotNull
    public static GlowOverride.RainbowGlowOverride rainbow(double speed) {
        final GlowOverride.RainbowGlowOverride g = new GlowOverride.RainbowGlowOverride();
        g.speed(speed);
        return g;
    }

    @NotNull
    public static GlowOverride.GradientGlowOverride gradient(@NotNull List<String> colors, int refreshInterval) {
        final GlowOverride.GradientGlowOverride g = new GlowOverride.GradientGlowOverride();
        g.colors(colors);
        g.refreshInterval(refreshInterval);
        return g;
    }

    @NotNull
    public static GlowOverride.CustomGlowOverride custom(@NotNull String handlerId) {
        final GlowOverride.CustomGlowOverride g = new GlowOverride.CustomGlowOverride();
        g.id(handlerId);
        return g;
    }
}

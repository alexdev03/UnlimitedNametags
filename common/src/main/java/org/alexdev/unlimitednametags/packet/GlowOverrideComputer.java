package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.config.ColorStrings;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.platform.NametagRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Resolves a {@link GlowOverride} to a 24-bit RGB glow color for the current tick.
 */
public final class GlowOverrideComputer {

    private GlowOverrideComputer() {
    }

    /**
     * @param glow already resolved (no reference indirection)
     */
    @NotNull
    public static OptionalInt compute(
            @Nullable GlowOverride glow,
            double elapsedSeconds,
            long monotonicTick,
            int effectiveGlowTickInterval,
            @NotNull UUID ownerId,
            @NotNull NametagRuntime runtime) {
        return compute(glow, elapsedSeconds, monotonicTick, effectiveGlowTickInterval, ownerId, runtime, null);
    }

    @NotNull
    public static OptionalInt compute(
            @Nullable GlowOverride glow,
            double elapsedSeconds,
            long monotonicTick,
            int effectiveGlowTickInterval,
            @NotNull UUID ownerId,
            @NotNull NametagRuntime runtime,
            @Nullable BiConsumer<String, Throwable> customGlowWarning) {
        if (glow == null || !glow.isActive()) {
            return OptionalInt.empty();
        }
        if (glow instanceof GlowOverride.FixedGlowOverride fixed) {
            final Integer rgb = ColorStrings.parseRgb(fixed.color());
            return rgb != null ? OptionalInt.of(rgb) : OptionalInt.empty();
        }
        if (glow instanceof GlowOverride.RainbowGlowOverride rainbow) {
            final double hue = (elapsedSeconds * rainbow.speed() * 360.0) % 360.0;
            return OptionalInt.of(hsvToRgb((float) hue, 1f, 1f));
        }
        if (glow instanceof GlowOverride.GradientGlowOverride gradient) {
            final List<String> colors = gradient.colors();
            if (colors == null || colors.size() < 2) {
                return OptionalInt.empty();
            }
            final int stepInterval = Math.max(1, Math.max(gradient.refreshInterval(), effectiveGlowTickInterval));
            final int step = (int) ((monotonicTick / stepInterval) % colors.size());
            final Integer rgb = ColorStrings.parseRgb(colors.get(step));
            return rgb != null ? OptionalInt.of(rgb) : OptionalInt.empty();
        }
        if (glow instanceof GlowOverride.CustomGlowOverride custom) {
            final String rawId = custom.id();
            if (rawId == null || rawId.isBlank()) {
                return OptionalInt.empty();
            }
            final CustomGlowHandler handler = runtime.resolveCustomGlowHandler(rawId.trim());
            if (handler == null) {
                return OptionalInt.empty();
            }
            try {
                final double scaledElapsed = elapsedSeconds * custom.speed();
                final GlowApplyContext ctx = new GlowApplyContext(
                        custom, scaledElapsed, monotonicTick, effectiveGlowTickInterval, ownerId);
                final Integer rgb = handler.apply(ctx);
                if (rgb == null) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(rgb & 0xFFFFFF);
            } catch (final Throwable ex) {
                if (customGlowWarning != null) {
                    customGlowWarning.accept(rawId.trim(), ex);
                }
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    public static boolean isAnimated(@Nullable GlowOverride glow) {
        if (glow == null || !glow.isActive()) {
            return false;
        }
        return glow instanceof GlowOverride.RainbowGlowOverride
                || glow instanceof GlowOverride.GradientGlowOverride
                || glow instanceof GlowOverride.CustomGlowOverride;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        final int h = (int) (hue / 60f) % 6;
        final float f = hue / 60f - (int) (hue / 60f);
        final float p = value * (1f - saturation);
        final float q = value * (1f - f * saturation);
        final float t = value * (1f - (1f - f) * saturation);
        return switch (h) {
            case 0 -> rgb(value, t, p);
            case 1 -> rgb(q, value, p);
            case 2 -> rgb(p, value, t);
            case 3 -> rgb(p, q, value);
            case 4 -> rgb(t, p, value);
            default -> rgb(value, p, q);
        };
    }

    private static int rgb(float r, float g, float b) {
        return ((clamp(r) & 0xFF) << 16) | ((clamp(g) & 0xFF) << 8) | (clamp(b) & 0xFF);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}

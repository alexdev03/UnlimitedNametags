package org.alexdev.unlimitednametags.glow;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.NametagCustomGlowContext;
import org.alexdev.unlimitednametags.config.NametagGlowOverrides;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Built-in glow presets and custom handlers registered on plugin enable.
 */
public final class DefaultNametagGlowRegistrations {

    public static final String HANDLER_GOLD_PULSE = "default_gold_pulse";

    public static final String PRESET_RAINBOW = "rainbow";
    public static final String PRESET_GRADIENT = "gradient";
    public static final String PRESET_GOLD_PULSE = "gold_pulse";

    private DefaultNametagGlowRegistrations() {
    }

    public static void register(@NotNull UnlimitedNameTags plugin) {
        plugin.registerNametagCustomGlowHandler(HANDLER_GOLD_PULSE, DefaultNametagGlowRegistrations::goldPulse);

        plugin.registerNametagGlowAnimation(PRESET_RAINBOW, NametagGlowOverrides.rainbow(1.0));
        plugin.registerNametagGlowAnimation(
                PRESET_GRADIENT,
                NametagGlowOverrides.gradient(List.of("#FF5555", "#55FF55", "#5555FF"), 10));
        plugin.registerNametagGlowAnimation(
                PRESET_GOLD_PULSE,
                NametagGlowOverrides.custom(HANDLER_GOLD_PULSE).speed(1.0));
    }

    @Nullable
    static Integer goldPulse(@NotNull NametagCustomGlowContext ctx) {
        final double wave = 0.5 + 0.5 * Math.sin(ctx.scaledElapsedSeconds() * Math.PI * 2.0);
        final int bright = 0xFFAA00;
        final int dim = 0x553300;
        final int r = blendChannel(bright >> 16, dim >> 16, wave);
        final int g = blendChannel(bright >> 8, dim >> 8, wave);
        final int b = blendChannel(bright, dim, wave);
        return (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int bright, int dim, double wave) {
        return (int) ((bright & 0xFF) * wave + (dim & 0xFF) * (1.0 - wave));
    }
}

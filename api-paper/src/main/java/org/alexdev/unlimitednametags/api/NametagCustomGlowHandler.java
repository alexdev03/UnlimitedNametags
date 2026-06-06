package org.alexdev.unlimitednametags.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for {@link org.alexdev.unlimitednametags.config.GlowOverride.CustomGlowOverride}
 * (YAML {@code type: custom}, or {@link org.alexdev.unlimitednametags.config.NametagGlowOverrides#custom}).
 * Register with {@link UNTPaperAPI#registerNametagCustomGlowHandler(String, NametagCustomGlowHandler)}.
 */
@FunctionalInterface
public interface NametagCustomGlowHandler {

    /**
     * @return 24-bit RGB ({@code 0xRRGGBB}), or {@code null} to clear glow for this tick
     */
    @Nullable
    Integer apply(@NotNull NametagCustomGlowContext context);
}

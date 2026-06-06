package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.GlowOverride;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tick context passed to {@link NametagCustomGlowHandler}.
 *
 * @param scaledElapsedSeconds wall seconds since this row's glow animation started, multiplied by {@link GlowOverride#speed()}
 * @param monotonicTick        server monotonic tick counter used for glow updates
 * @param effectiveGlowTickInterval tick stride for this display row ({@code glowInterval} or global default)
 */
public record NametagCustomGlowContext(
        @NotNull GlowOverride.CustomGlowOverride glow,
        double scaledElapsedSeconds,
        long monotonicTick,
        int effectiveGlowTickInterval,
        @NotNull UUID ownerId) {
}

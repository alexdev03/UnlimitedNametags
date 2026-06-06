package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.config.GlowOverride;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Tick context for {@link CustomGlowHandler} ({@code type: custom} glow).
 */
public record GlowApplyContext(
        @NotNull GlowOverride.CustomGlowOverride glow,
        double scaledElapsedSeconds,
        long monotonicTick,
        int effectiveGlowTickInterval,
        @NotNull UUID ownerId) {
}

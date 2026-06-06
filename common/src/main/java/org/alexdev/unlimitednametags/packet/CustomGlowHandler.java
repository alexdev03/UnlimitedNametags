package org.alexdev.unlimitednametags.packet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface CustomGlowHandler {

    /**
     * @return 24-bit RGB ({@code 0xRRGGBB}), or {@code null} to clear glow for this tick
     */
    @Nullable
    Integer apply(@NotNull GlowApplyContext context);
}

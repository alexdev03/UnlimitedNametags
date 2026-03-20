package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for {@link DisplayAnimation.CustomDisplayAnimation} (YAML {@code type: custom}, or {@link org.alexdev.unlimitednametags.config.NametagDisplayAnimations#custom}).
 * Register with {@link UnlimitedNameTagsPlugin#registerNametagCustomAnimation(String, NametagCustomAnimationHandler)}.
 * <p>
 * {@code scaledElapsedSeconds} is wall time since the animation started for this row, multiplied by {@link DisplayAnimation#speed()}.
 */
@FunctionalInterface
public interface NametagCustomAnimationHandler {

    void apply(@NotNull NametagAnimationTarget target, @NotNull DisplayAnimation.CustomDisplayAnimation animation, double scaledElapsedSeconds);
}

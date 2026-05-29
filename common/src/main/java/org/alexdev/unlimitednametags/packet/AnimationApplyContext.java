package org.alexdev.unlimitednametags.packet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Inputs for applying a {@link org.alexdev.unlimitednametags.config.DisplayAnimation} pose.
 */
public record AnimationApplyContext(
    @NotNull AnimationPoseTarget tag,
    double scaledElapsedSeconds,
    @Nullable Function<String, CustomDisplayAnimationHandler> customHandlers,
    @Nullable BiConsumer<String, Throwable> customAnimationWarning) {
}

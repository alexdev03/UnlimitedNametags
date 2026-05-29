package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Entry point for display-group animation ticks.
 */
public final class DisplayAnimationComputer {

  private DisplayAnimationComputer() {
  }

  public static void apply(
      @NotNull AnimationPoseTarget tag,
      @Nullable DisplayAnimation anim,
      double elapsedSeconds,
      @Nullable Function<String, CustomDisplayAnimationHandler> customHandlers,
      @Nullable BiConsumer<String, Throwable> customAnimationWarning) {
    if (anim == null || !anim.isAnimating()) {
      tag.clearAnimationPose();
      return;
    }
    anim.applyPose(new AnimationApplyContext(
        tag,
        elapsedSeconds * anim.speed(),
        customHandlers,
        customAnimationWarning));
  }
}

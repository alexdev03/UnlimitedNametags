package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface CustomDisplayAnimationHandler {

  void apply(
      @NotNull AnimationPoseTarget target,
      @NotNull DisplayAnimation.CustomDisplayAnimation animation,
      double scaledElapsedSeconds);
}

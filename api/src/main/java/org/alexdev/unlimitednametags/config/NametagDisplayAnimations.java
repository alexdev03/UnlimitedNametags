package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;

/**
 * Factory helpers for {@link DisplayAnimation} subtypes (for use from code and {@link UNTAPI}).
 * YAML can use the same shapes under {@code animation:} with {@code type: ...}.
 */
public final class NametagDisplayAnimations {

    private NametagDisplayAnimations() {
    }

    @NotNull
    public static DisplayAnimation.RotateDisplayAnimation rotateAxis(@NotNull String axis, double degreesPerSecond) {
        final DisplayAnimation.RotateDisplayAnimation a = new DisplayAnimation.RotateDisplayAnimation();
        a.axis(axis);
        a.degreesPerSecond(degreesPerSecond);
        return a;
    }

    @NotNull
    public static DisplayAnimation.RotateDisplayAnimation rotateY(double degreesPerSecond) {
        return rotateAxis("Y", degreesPerSecond);
    }

    @NotNull
    public static DisplayAnimation.BobDisplayAnimation bob(double amplitude, double bobsPerSecond) {
        final DisplayAnimation.BobDisplayAnimation a = new DisplayAnimation.BobDisplayAnimation();
        a.amplitude(amplitude);
        a.bobsPerSecond(bobsPerSecond);
        return a;
    }

    @NotNull
    public static DisplayAnimation.DvdBounceDisplayAnimation dvdBounce(double halfWidth, double halfDepth, double pace) {
        final DisplayAnimation.DvdBounceDisplayAnimation a = new DisplayAnimation.DvdBounceDisplayAnimation();
        a.halfWidth(halfWidth);
        a.halfDepth(halfDepth);
        a.pace(pace);
        return a;
    }

    @NotNull
    public static DisplayAnimation.PulseScaleDisplayAnimation pulseScale(double minMultiplier, double maxMultiplier, double pulsesPerSecond) {
        final DisplayAnimation.PulseScaleDisplayAnimation a = new DisplayAnimation.PulseScaleDisplayAnimation();
        a.minMultiplier(minMultiplier);
        a.maxMultiplier(maxMultiplier);
        a.pulsesPerSecond(pulsesPerSecond);
        return a;
    }

    @NotNull
    public static DisplayAnimation.WiggleDisplayAnimation wiggle(double amplitudeDegrees, double wigglesPerSecond) {
        final DisplayAnimation.WiggleDisplayAnimation a = new DisplayAnimation.WiggleDisplayAnimation();
        a.amplitudeDegrees(amplitudeDegrees);
        a.wigglesPerSecond(wigglesPerSecond);
        return a;
    }

    @NotNull
    public static DisplayAnimation.OrbitDisplayAnimation orbit(double radius, double rotationsPerSecond) {
        final DisplayAnimation.OrbitDisplayAnimation a = new DisplayAnimation.OrbitDisplayAnimation();
        a.radius(radius);
        a.rotationsPerSecond(rotationsPerSecond);
        return a;
    }

    @NotNull
    public static DisplayAnimation.CustomDisplayAnimation custom(@NotNull String id) {
        final DisplayAnimation.CustomDisplayAnimation a = new DisplayAnimation.CustomDisplayAnimation();
        a.id(id);
        return a;
    }
}

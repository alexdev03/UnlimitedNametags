package org.alexdev.unlimitednametags.config;

import com.github.retrooper.packetevents.util.Quaternion4f;
import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Polymorphic;
import de.exlll.configlib.PolymorphicTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.alexdev.unlimitednametags.packet.AnimationApplyContext;
import org.alexdev.unlimitednametags.packet.AnimationPoseTarget;
import org.alexdev.unlimitednametags.packet.CustomDisplayAnimationHandler;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Optional per-display-group animation. YAML under {@code animation:} with
 * {@code type: rotate | bob | dvd_bounce | pulse_scale | wiggle | orbit | custom}.
 * Optional {@code cullBeyondBlocks} (on any type) skips pose updates when no viewer is within range.
 * From code, prefer {@link NametagDisplayAnimations} to build instances.
 * <p>
 * {@link CustomDisplayAnimation} dispatches to handlers registered via
 * {@code UNTPaperAPI.registerNametagCustomAnimation} / {@code UnlimitedNameTagsPluginPaper.registerNametagCustomAnimation}.
 */
@Configuration
@Getter
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
@Polymorphic
@PolymorphicTypes({
        @PolymorphicTypes.Type(type = DisplayAnimation.RotateDisplayAnimation.class, alias = "rotate"),
        @PolymorphicTypes.Type(type = DisplayAnimation.BobDisplayAnimation.class, alias = "bob"),
        @PolymorphicTypes.Type(type = DisplayAnimation.DvdBounceDisplayAnimation.class, alias = "dvd_bounce"),
        @PolymorphicTypes.Type(type = DisplayAnimation.PulseScaleDisplayAnimation.class, alias = "pulse_scale"),
        @PolymorphicTypes.Type(type = DisplayAnimation.WiggleDisplayAnimation.class, alias = "wiggle"),
        @PolymorphicTypes.Type(type = DisplayAnimation.OrbitDisplayAnimation.class, alias = "orbit"),
        @PolymorphicTypes.Type(type = DisplayAnimation.CustomDisplayAnimation.class, alias = "custom")
})
public abstract class DisplayAnimation {

    private static final float TICK_DT = 0.05f;

    @Comment("If false, this animation does nothing.")
    private boolean enabled = true;

    @Comment("Tempo multiplier (1 = default speed for the chosen type).")
    private double speed = 1.0;

    @Comment({
            "If > 0, animation pose updates are skipped when every current viewer is farther than this (blocks) from the nametag owner (same world).",
            "Clears motion while nobody is close enough to see it meaningfully; resumes smoothly when someone enters range. 0 = no distance culling."
    })
    private double cullBeyondBlocks = 0.0;

    @Comment({
            "Optional string map for add-ons or future use (readable via API; built-in animators ignore unknown keys).",
            "YAML example: customProperties: { my_plugin_mode: sparkle }"
    })
    private Map<String, String> customProperties = new LinkedHashMap<>();

    public boolean isAnimating() {
        return enabled && speed > 0;
    }

    /**
     * Applies this animation's pose to the target for the given tick context.
     */
    public abstract void applyPose(@NotNull AnimationApplyContext ctx);

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class RotateDisplayAnimation extends DisplayAnimation {

        @Comment("Y = spin like a turntable; X / Z = tilt plane; XYZ = slow tumble (combined axes).")
        private String axis = "Y";

        @Comment("Degrees per second at speed 1.")
        private double degreesPerSecond = 90.0;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final double t = ctx.scaledElapsedSeconds();
            float angle = (float) Math.toRadians(degreesPerSecond * t);
            String ax = axis == null ? "Y" : axis.trim().toUpperCase();
            Quaternion4f q;
            switch (ax) {
                case "X" -> q = axisAngle(1f, 0f, 0f, angle);
                case "Z" -> q = axisAngle(0f, 0f, 1f, angle);
                case "XYZ", "ALL", "TUMBLE" -> {
                    float a1 = (float) Math.toRadians(47 * t);
                    float a2 = (float) Math.toRadians(61 * t);
                    float a3 = (float) Math.toRadians(53 * t);
                    q = multiply(multiply(axisAngle(1f, 0f, 0f, a1), axisAngle(0f, 1f, 0f, a2)), axisAngle(0f, 0f, 1f, a3));
                }
                default -> q = axisAngle(0f, 1f, 0f, angle);
            }
            tag.setAnimationPose(0f, 0f, 0f, q, 1f);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class BobDisplayAnimation extends DisplayAnimation {

        @Comment("Vertical motion amplitude in blocks (local Y).")
        private double amplitude = 0.06;

        @Comment("Full up-down cycles per second at speed 1.")
        private double bobsPerSecond = 1.0;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final double t = ctx.scaledElapsedSeconds();
            float ty = (float) (amplitude * Math.sin(Math.PI * 2.0 * bobsPerSecond * t));
            tag.setAnimationPose(0f, ty, 0f, identity(), 1f);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class DvdBounceDisplayAnimation extends DisplayAnimation {

        @Comment("Half-width of the bounce box along local X (blocks).")
        private double halfWidth = 0.12;

        @Comment("Half-height along local Z (blocks); classic DVD screensaver in the horizontal plane above the tag stack.")
        private double halfDepth = 0.1;

        @Comment("Travel speed scale (blocks / second at speed 1).")
        private double pace = 0.35;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            float hw = (float) Math.max(0.001, halfWidth);
            float hd = (float) Math.max(0.001, halfDepth);
            double paceScaled = pace * speed();
            float step = (float) (paceScaled * TICK_DT);

            if (!tag.isAnimDvdInitialized()) {
                Random rnd = new Random(tag.getNametagDisplayEntityId() * 31L + tag.getDvdRandomSeed());
                tag.setAnimDvdX((rnd.nextFloat() - 0.5f) * hw);
                tag.setAnimDvdZ((rnd.nextFloat() - 0.5f) * hd);
                double ang = rnd.nextDouble() * Math.PI * 2.0;
                tag.setAnimDvdVx((float) (Math.cos(ang) * step));
                tag.setAnimDvdVz((float) (Math.sin(ang) * step));
                tag.setAnimDvdInitialized(true);
            }

            float x = tag.getAnimDvdX() + tag.getAnimDvdVx();
            float z = tag.getAnimDvdZ() + tag.getAnimDvdVz();
            float vx = tag.getAnimDvdVx();
            float vz = tag.getAnimDvdVz();

            if (x > hw) {
                x = hw;
                vx = -Math.abs(vx);
            } else if (x < -hw) {
                x = -hw;
                vx = Math.abs(vx);
            }
            if (z > hd) {
                z = hd;
                vz = -Math.abs(vz);
            } else if (z < -hd) {
                z = -hd;
                vz = Math.abs(vz);
            }

            if (Math.abs(vx) < 1e-5f && Math.abs(vz) < 1e-5f) {
                vx = step;
                vz = step * 0.73f;
            }

            tag.setAnimDvdX(x);
            tag.setAnimDvdZ(z);
            tag.setAnimDvdVx(vx);
            tag.setAnimDvdVz(vz);
            tag.setAnimationPose(x, 0f, z, identity(), 1f);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class PulseScaleDisplayAnimation extends DisplayAnimation {

        @Comment("Minimum scale multiplier (relative to the nametag line scale).")
        private double minMultiplier = 0.92;

        @Comment("Maximum scale multiplier.")
        private double maxMultiplier = 1.08;

        @Comment("Full breathe cycles per second at speed 1.")
        private double pulsesPerSecond = 1.0;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final double t = ctx.scaledElapsedSeconds();
            double min = Math.min(minMultiplier, maxMultiplier);
            double max = Math.max(minMultiplier, maxMultiplier);
            double wave = 0.5 + 0.5 * Math.sin(Math.PI * 2.0 * pulsesPerSecond * t);
            float mul = (float) (min + (max - min) * wave);
            tag.setAnimationPose(0f, 0f, 0f, identity(), mul);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class WiggleDisplayAnimation extends DisplayAnimation {

        @Comment("Peak tilt amplitude in degrees.")
        private double amplitudeDegrees = 10.0;

        @Comment("Wiggle cycles per second at speed 1.")
        private double wigglesPerSecond = 2.0;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final double t = ctx.scaledElapsedSeconds();
            float rx = (float) Math.toRadians(amplitudeDegrees * Math.sin(Math.PI * 2.0 * wigglesPerSecond * t));
            float rz = (float) Math.toRadians(amplitudeDegrees * Math.cos(Math.PI * 2.0 * wigglesPerSecond * 1.17 * t));
            Quaternion4f q = multiply(axisAngle(1f, 0f, 0f, rx), axisAngle(0f, 0f, 1f, rz));
            tag.setAnimationPose(0f, 0f, 0f, q, 1f);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class OrbitDisplayAnimation extends DisplayAnimation {

        @Comment("Orbit radius in blocks (local XZ plane).")
        private double radius = 0.1;

        @Comment("Full orbits per second at speed 1.")
        private double rotationsPerSecond = 0.5;

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final double t = ctx.scaledElapsedSeconds();
            double r = Math.max(0, radius);
            float ang = (float) (Math.PI * 2.0 * rotationsPerSecond * t);
            float tx = (float) (r * Math.cos(ang));
            float tz = (float) (r * Math.sin(ang));
            tag.setAnimationPose(tx, 0f, tz, identity(), 1f);
        }
    }

    @Configuration
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    public static class CustomDisplayAnimation extends DisplayAnimation {

        @Comment("Must match a handler id passed to registerNametagCustomAnimation (UNTPaperAPI / UnlimitedNameTagsPluginPaper).")
        private String id = "";

        @Override
        public void applyPose(@NotNull AnimationApplyContext ctx) {
            final AnimationPoseTarget tag = ctx.tag();
            final String rawId = id;
            if (rawId == null || rawId.isBlank()) {
                tag.clearAnimationPose();
                return;
            }
            if (ctx.customHandlers() == null) {
                tag.clearAnimationPose();
                return;
            }
            final CustomDisplayAnimationHandler handler = ctx.customHandlers().apply(rawId.trim());
            if (handler == null) {
                tag.clearAnimationPose();
                return;
            }
            try {
                handler.apply(tag, this, ctx.scaledElapsedSeconds());
            } catch (final Throwable ex) {
                if (ctx.customAnimationWarning() != null) {
                    ctx.customAnimationWarning().accept(rawId.trim(), ex);
                }
                tag.clearAnimationPose();
            }
        }
    }

    private static @NotNull Quaternion4f identity() {
        return new Quaternion4f(0f, 0f, 0f, 1f);
    }

    private static @NotNull Quaternion4f axisAngle(float ax, float ay, float az, float angleRad) {
        float half = angleRad * 0.5f;
        float s = (float) Math.sin(half);
        float c = (float) Math.cos(half);
        return new Quaternion4f(ax * s, ay * s, az * s, c);
    }

    private static @NotNull Quaternion4f multiply(@NotNull Quaternion4f q1, @NotNull Quaternion4f q2) {
        float x1 = q1.getX(), y1 = q1.getY(), z1 = q1.getZ(), w1 = q1.getW();
        float x2 = q2.getX(), y2 = q2.getY(), z2 = q2.getZ(), w2 = q2.getW();
        float x = w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2;
        float y = w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2;
        float z = w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2;
        float w = w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2;
        return new Quaternion4f(x, y, z, w);
    }
}

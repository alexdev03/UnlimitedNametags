package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.util.Quaternion4f;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.NametagCustomAnimationHandler;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.logging.Level;

/**
 * Computes local translation, left rotation, and scale multiplier for display-group animations.
 */
final class DisplayAnimationComputer {

    private static final float TICK_DT = 0.05f;

    private DisplayAnimationComputer() {
    }

    static void apply(@NotNull PacketNameTag tag, @Nullable DisplayAnimation anim, double elapsedSeconds) {
        if (anim == null || !anim.isAnimating()) {
            tag.clearAnimationPose();
            return;
        }
        final double s = anim.speed();
        final double t = elapsedSeconds * s;
        if (anim instanceof DisplayAnimation.RotateDisplayAnimation r) {
            applyRotate(tag, r, t);
        } else if (anim instanceof DisplayAnimation.BobDisplayAnimation b) {
            applyBob(tag, b, t);
        } else if (anim instanceof DisplayAnimation.DvdBounceDisplayAnimation d) {
            applyDvd(tag, d);
        } else if (anim instanceof DisplayAnimation.PulseScaleDisplayAnimation p) {
            applyPulse(tag, p, t);
        } else if (anim instanceof DisplayAnimation.WiggleDisplayAnimation w) {
            applyWiggle(tag, w, t);
        } else if (anim instanceof DisplayAnimation.OrbitDisplayAnimation o) {
            applyOrbit(tag, o, t);
        } else if (anim instanceof DisplayAnimation.CustomDisplayAnimation c) {
            applyCustom(tag, anim, c, t);
        } else {
            tag.clearAnimationPose();
        }
    }

    private static void applyCustom(
            @NotNull PacketNameTag tag,
            @NotNull DisplayAnimation anim,
            @NotNull DisplayAnimation.CustomDisplayAnimation c,
            double scaledElapsedSeconds) {
        final String rawId = c.id();
        if (rawId == null || rawId.isBlank()) {
            tag.clearAnimationPose();
            return;
        }
        final UnlimitedNameTags plugin = tag.getPlugin();
        final @Nullable NametagCustomAnimationHandler handler = plugin.getNametagCustomAnimationHandler(rawId.trim());
        if (handler == null) {
            tag.clearAnimationPose();
            return;
        }
        try {
            handler.apply(tag, c, scaledElapsedSeconds);
        } catch (final Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Nametag custom animation '" + rawId + "'", ex);
            tag.clearAnimationPose();
        }
    }

    private static void applyRotate(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.RotateDisplayAnimation r, double t) {
        float angle = (float) Math.toRadians(r.degreesPerSecond() * t);
        String ax = r.axis() == null ? "Y" : r.axis().trim().toUpperCase();
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

    private static void applyBob(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.BobDisplayAnimation b, double t) {
        double amp = b.amplitude();
        double freq = b.bobsPerSecond();
        float ty = (float) (amp * Math.sin(Math.PI * 2.0 * freq * t));
        tag.setAnimationPose(0f, ty, 0f, identity(), 1f);
    }

    private static void applyDvd(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.DvdBounceDisplayAnimation d) {
        float hw = (float) Math.max(0.001, d.halfWidth());
        float hd = (float) Math.max(0.001, d.halfDepth());
        double pace = d.pace() * d.speed();
        float step = (float) (pace * TICK_DT);

        if (!tag.animDvdInitialized) {
            Random rnd = new Random(tag.getEntityId() * 31L + tag.getOwner().getUniqueId().getLeastSignificantBits());
            tag.animDvdX = (rnd.nextFloat() - 0.5f) * hw;
            tag.animDvdZ = (rnd.nextFloat() - 0.5f) * hd;
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            tag.animDvdVx = (float) (Math.cos(ang) * step);
            tag.animDvdVz = (float) (Math.sin(ang) * step);
            tag.animDvdInitialized = true;
        }

        float x = tag.animDvdX + tag.animDvdVx;
        float z = tag.animDvdZ + tag.animDvdVz;
        float vx = tag.animDvdVx;
        float vz = tag.animDvdVz;

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

        tag.animDvdX = x;
        tag.animDvdZ = z;
        tag.animDvdVx = vx;
        tag.animDvdVz = vz;
        tag.setAnimationPose(x, 0f, z, identity(), 1f);
    }

    private static void applyPulse(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.PulseScaleDisplayAnimation p, double t) {
        double min = Math.min(p.minMultiplier(), p.maxMultiplier());
        double max = Math.max(p.minMultiplier(), p.maxMultiplier());
        double freq = p.pulsesPerSecond();
        double wave = 0.5 + 0.5 * Math.sin(Math.PI * 2.0 * freq * t);
        float mul = (float) (min + (max - min) * wave);
        tag.setAnimationPose(0f, 0f, 0f, identity(), mul);
    }

    private static void applyWiggle(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.WiggleDisplayAnimation w, double t) {
        double deg = w.amplitudeDegrees();
        double freq = w.wigglesPerSecond();
        float rx = (float) Math.toRadians(deg * Math.sin(Math.PI * 2.0 * freq * t));
        float rz = (float) Math.toRadians(deg * Math.cos(Math.PI * 2.0 * freq * 1.17 * t));
        Quaternion4f q = multiply(axisAngle(1f, 0f, 0f, rx), axisAngle(0f, 0f, 1f, rz));
        tag.setAnimationPose(0f, 0f, 0f, q, 1f);
    }

    private static void applyOrbit(@NotNull PacketNameTag tag, @NotNull DisplayAnimation.OrbitDisplayAnimation o, double t) {
        double radius = Math.max(0, o.radius());
        double rot = o.rotationsPerSecond();
        float ang = (float) (Math.PI * 2.0 * rot * t);
        float tx = (float) (radius * Math.cos(ang));
        float tz = (float) (radius * Math.sin(ang));
        tag.setAnimationPose(tx, 0f, tz, identity(), 1f);
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

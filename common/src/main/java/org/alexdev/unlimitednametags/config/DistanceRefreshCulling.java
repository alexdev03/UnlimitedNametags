package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;

/**
 * Computes a distance-aware refresh cadence for nametag placeholder updates.
 * <p>
 * Formula:
 * <pre>
 * n = clamp((distance - nearDistance) / (maxDistance - nearDistance), 0, 1)
 * interval = baseInterval + (maxInterval - baseInterval) * n^curve
 * </pre>
 * Near players stay at the base refresh interval; farther players still refresh, but asymptotically slow down to
 * {@code maxInterval}.
 */
public final class DistanceRefreshCulling {

    private DistanceRefreshCulling() {
    }

    public static int intervalTicks(final int baseInterval, @NotNull Settings.DistanceRefreshCulling config,
            final double nearestViewerDistance) {
        if (!config.isEnabled()) {
            return Math.max(1, baseInterval);
        }
        return intervalTicks(baseInterval, config.getMaxInterval(), config.getNearDistance(), config.getMaxDistance(),
                config.getCurve(), nearestViewerDistance);
    }

    public static int intervalTicks(final int baseInterval, final int maxInterval, final double nearDistance,
            final double maxDistance, final double curve, final double nearestViewerDistance) {
        final int safeBase = Math.max(1, baseInterval);
        final int safeMax = Math.max(safeBase, maxInterval);
        if (Double.isNaN(nearestViewerDistance) || nearestViewerDistance <= nearDistance) {
            return safeBase;
        }
        if (maxDistance <= nearDistance) {
            return safeMax;
        }

        final double normalized = clamp01((nearestViewerDistance - nearDistance) / (maxDistance - nearDistance));
        final double exponent = curve > 0.0 && Double.isFinite(curve) ? curve : 1.0;
        final double factor = Math.pow(normalized, exponent);
        return (int) Math.ceil(safeBase + (safeMax - safeBase) * factor);
    }

    public static boolean shouldRefresh(final long elapsedTicks, final int effectiveInterval) {
        return elapsedTicks >= Math.max(1, effectiveInterval);
    }

    private static double clamp01(final double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }
}

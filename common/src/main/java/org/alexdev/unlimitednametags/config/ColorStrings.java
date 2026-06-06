package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses hex (#RRGGBB) or comma-separated RGB color strings.
 */
public final class ColorStrings {

    private ColorStrings() {
    }

    public static boolean isValid(@Nullable String color) {
        return parseRgb(color) != null;
    }

    /**
     * @return 24-bit RGB (no alpha), or {@code null} if invalid
     */
    @Nullable
    public static Integer parseRgb(@Nullable String color) {
        if (color == null) {
            return null;
        }
        final String c = color.trim();
        if (c.isEmpty()) {
            return null;
        }
        try {
            if (c.startsWith("#")) {
                final int rgb = Integer.parseInt(c.substring(1), 16);
                return rgb & 0xFFFFFF;
            }
            final String[] p = c.split(",");
            if (p.length < 3) {
                return null;
            }
            final int r = clamp(Integer.parseInt(p[0].trim()));
            final int g = clamp(Integer.parseInt(p[1].trim()));
            final int b = clamp(Integer.parseInt(p[2].trim()));
            return (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}

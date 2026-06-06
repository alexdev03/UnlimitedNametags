package org.alexdev.unlimitednametags.config;

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
                if (c.length() != 7) {
                    return null;
                }
                final int rgb = Integer.parseInt(c.substring(1), 16);
                return rgb;
            }
            final String[] p = c.split(",", -1);
            if (p.length != 3) {
                return null;
            }
            final int r = parseComponent(p[0]);
            final int g = parseComponent(p[1]);
            final int b = parseComponent(p[2]);
            return (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseComponent(@Nullable String raw) {
        if (raw == null) {
            throw new NumberFormatException("missing RGB component");
        }
        final int value = Integer.parseInt(raw.trim());
        if (value < 0 || value > 255) {
            throw new NumberFormatException("RGB component out of range");
        }
        return value;
    }
}

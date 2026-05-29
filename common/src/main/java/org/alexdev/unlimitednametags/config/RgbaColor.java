package org.alexdev.unlimitednametags.config;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-neutral ARGB color (alpha in high byte).
 */
public record RgbaColor(int argb) {

  @NotNull
  public static RgbaColor transparent() {
    return new RgbaColor(0);
  }

  @NotNull
  public static RgbaColor fromRgb(int red, int green, int blue, int alpha) {
    final int a = clamp(alpha);
    final int r = clamp(red);
    final int g = clamp(green);
    final int b = clamp(blue);
    return new RgbaColor((a << 24) | (r << 16) | (g << 8) | b);
  }

  @NotNull
  public static RgbaColor fromHexRgb(@NotNull String hex, int alpha) {
    final String h = hex.startsWith("#") ? hex.substring(1) : hex;
    final int rgb = Integer.parseInt(h, 16);
    return fromRgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}

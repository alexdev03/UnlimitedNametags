package org.alexdev.unlimitednametags.config;

import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;

public final class BukkitColors {

  private BukkitColors() {
  }

  @NotNull
  public static Color toBukkit(@NotNull RgbaColor color) {
    return Color.fromARGB(color.argb());
  }
}

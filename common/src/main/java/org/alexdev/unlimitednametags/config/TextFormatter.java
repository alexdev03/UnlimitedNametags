package org.alexdev.unlimitednametags.config;

/**
 * Text markup mode stored in settings.yml ({@code behavior.format}, see {@link Settings.Behavior}).
 * Bukkit formatting is applied via {@code Formatter} in the api-paper module.
 */
public enum TextFormatter {
  MINIMESSAGE,
  LEGACY,
  UNIVERSAL
}

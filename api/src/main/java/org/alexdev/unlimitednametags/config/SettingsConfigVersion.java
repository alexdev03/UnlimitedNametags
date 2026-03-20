package org.alexdev.unlimitednametags.config;

/**
 * Version of the {@code settings.yml} schema. Increment {@link #CURRENT} when the structure changes and add a migrator.
 */
public final class SettingsConfigVersion {

    private SettingsConfigVersion() {
    }

    /**
     * Latest schema ({@code displayGroups}, {@link Settings.DisplayGroup}, {@code displayType}, etc.).
     */
    public static final int CURRENT = 2;

    /**
     * Legacy: each {@code NameTag} had top-level {@code lines}, {@code background}, {@code scale} (and optional {@code yOffset}).
     */
    public static final int LEGACY_FLAT_NAMETAG = 1;
}

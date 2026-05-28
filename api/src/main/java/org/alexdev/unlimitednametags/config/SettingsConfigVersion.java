package org.alexdev.unlimitednametags.config;

/**
 * Version of the {@code settings.yml} schema. Increment {@link #CURRENT} when the structure changes and add a migrator.
 */
public final class SettingsConfigVersion {

    private SettingsConfigVersion() {
    }

    /**
     * Latest schema: unified {@link Settings.Background} (no {@code type:} discriminator) + sectioned settings
     * ({@code behavior}, {@code visibility}, {@code performance}).
     */
    public static final int CURRENT = 4;

    /**
     * {@code displayGroups} with {@link Settings.DisplayGroup}, structured {@code lines} as {@code {text, when?}} objects.
     */
    public static final int STRUCTURED_LINE_DISPLAY_GROUPS = 3;

    /**
     * Display groups with {@code lines} represented as raw strings.
     */
    public static final int STRING_LINE_DISPLAY_GROUPS = 2;

    /**
     * Legacy: each {@code NameTag} had top-level {@code lines}, {@code background}, {@code scale} (and optional {@code yOffset}).
     */
    public static final int LEGACY_FLAT_NAMETAG = 1;
}

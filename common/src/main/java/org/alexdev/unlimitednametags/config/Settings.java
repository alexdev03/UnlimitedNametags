package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@Configuration
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@Getter
public class Settings {

    @Comment({
            "Schema version for settings.yml (managed by the plugin; do not lower).",
            "1 = flat NameTag, 2 = displayGroups with string lines, 3 = displayGroups with structured lines,",
            "4 = unified Background (no type: discriminator) + sectioned settings,",
            "5 = throughWallMode, 6 = glowAnimations + per-row glow,",
            "7 = distance refresh culling (current)."
    })
    private int configVersion = SettingsConfigVersion.CURRENT;

    private Map<String, NameTag> nameTags = defaultNameTags();

    @Comment({
            "Reusable glow animation presets. Reference from displayGroups via glow.type: reference.",
            "Built-in ids: rainbow, gradient, gold_pulse (custom handler registered by the plugin)."
    })
    private Map<String, GlowOverride> glowAnimations = defaultGlowAnimations();

    @Comment("General nametag behavior settings.")
    private Behavior behavior = new Behavior();

    @Comment("Visibility and opacity settings.")
    private Visibility visibility = new Visibility();

    @Comment("Performance and caching settings.")
    private Performance performance = new Performance();

    @Comment("Match PAPI output strings. Quote reserved YAML 1.1 words: use placeholder: \"Yes\" not Yes (otherwise they become booleans).")
    private Map<String, List<PlaceholderReplacement>> placeholdersReplacements = defaultPlaceholdersReplacements();

    // ─── Logic ────────────────────────────────────────────────────────────────

    @NotNull
    public NameTag resolveNametag(@NotNull Predicate<String> hasPermission) {
        return nameTags.entrySet().stream()
                .filter(entry -> entry.getValue().permission == null || hasPermission.test(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nameTags.get("default"));
    }

    protected Optional<NameTag> getDefaultNameTag() {
        return Optional.ofNullable(nameTags.get("default"));
    }

    // ─── Defaults ─────────────────────────────────────────────────────────────

    private static Map<String, NameTag> defaultNameTags() {
        final Map<String, NameTag> m = new LinkedHashMap<>();
        m.put("staffer", new NameTag("nametag.staffer", List.of(
                DisplayGroup.builder()
                        .line("Staffer %luckperms_prefix% %player_name% %luckperms_suffix%")
                        .background(Background.ofRGB(false, 255, 0, 0, 255, true, false))
                        .scale(1f).yOffset(0.2f)
                        .build())));
        m.put("default", new NameTag("nametag.default", List.of(
                DisplayGroup.builder()
                        .line("%luckperms_prefix% %player_name% %luckperms_suffix%")
                        .background(Background.ofRGB(false, 255, 0, 0, 255, true, false))
                        .scale(1f).yOffset(0.2f)
                        .when("%player_health% > 10")
                        .build(),
                DisplayGroup.builder()
                        .line("Healthy Player!")
                        .background(Background.ofHex(false, "#ffffff", 255, false, false))
                        .scale(1f).yOffset(1.2f)
                        .build())));
        return m;
    }

    private static Map<String, GlowOverride> defaultGlowAnimations() {
        final Map<String, GlowOverride> presets = new LinkedHashMap<>();
        presets.put("rainbow", NametagGlowOverrides.rainbow(1.0));
        presets.put("gradient", NametagGlowOverrides.gradient(List.of("#FF5555", "#55FF55", "#5555FF"), 10));
        final GlowOverride.CustomGlowOverride goldPulse = NametagGlowOverrides.custom("default_gold_pulse");
        goldPulse.speed(1.0);
        presets.put("gold_pulse", goldPulse);
        return presets;
    }

    private static Map<String, List<PlaceholderReplacement>> defaultPlaceholdersReplacements() {
        final Map<String, List<PlaceholderReplacement>> m = new LinkedHashMap<>();
        m.put("%advancedvanish_is_vanished%", List.of(
                new PlaceholderReplacement("Yes", " &7[V]&r"),
                new PlaceholderReplacement("No", "")));
        return m;
    }

    // ─── Sub-sections ─────────────────────────────────────────────────────────

    @Configuration
    @Getter
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class Behavior {

        @Comment("Ticks between nametag placeholder refreshes.")
        private int taskInterval = 20;

        @Comment({
                "Ticks between display animation updates when a display group has no animation_interval.",
                "Use 0 to use the same interval as taskInterval (nametag placeholder refresh cadence)."
        })
        private int displayAnimationInterval = 1;

        @Comment("Global vertical offset (blocks) added to all nametags.")
        private float yOffset = 0.3f;

        @Comment({
                "Divided by 160 and sent to clients as the display entity view_range (vanilla default = 1.0).",
                "~blocks ≈ (viewDistance/160)*64. e.g. 60 → ~24 blocks, 160 → ~64 blocks."
        })
        private float viewDistance = 60;

        @Comment({
                "When true, displayGroups are laid out as a compact vertical stack after placeholders are resolved.",
                "Empty text groups and inactive groups do not reserve vertical space."
        })
        private boolean compactDisplayGroupStack = false;

        @Comment("Estimated height (blocks) of one resolved text line when compactDisplayGroupStack is enabled.")
        private float displayGroupLineHeightBlocks = 0.25f;

        @Comment("Whether to disable the default name tag or not.")
        private boolean disableDefaultNameTag = true;

        @Comment("Skip the team internal cache and force all default nametags invisible. " +
                "Useful when NPCs share names with online players.")
        private boolean forceDisableDefaultNameTag = false;

        @Setter
        @Comment("The default billboard constraints for the nametag (CENTER, HORIZONTAL, VERTICAL, FIXED).")
        private AbstractDisplayMeta.BillboardConstraints defaultBillboard = AbstractDisplayMeta.BillboardConstraints.CENTER;

        @Setter
        @Comment("""
                Which text formatter to use (MINIMESSAGE, MINEDOWN, LEGACY or UNIVERSAL).\s
                UNIVERSAL is the most resource intensive but supports all formatting options (except MINEDOWN).\s
                (&x&0&8&4&c&f&bc LEGACY OF LEGACY - &#084cfbc LEGACY - &#084cfbc& MINEDOWN - <color:#084cfbc> MINIMESSAGE)""")
        private TextFormatter format = TextFormatter.MINIMESSAGE;

        @Comment("Remove empty lines from nametag display.")
        private boolean removeEmptyLines = true;

        public float getViewDistance() {
            return viewDistance / 160;
        }

        public int resolveDisplayAnimationTickInterval() {
            if (displayAnimationInterval > 0) {
                return displayAnimationInterval;
            }
            return Math.max(1, taskInterval);
        }
    }

    @Configuration
    @Getter
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class Visibility {

        @Comment("Opacity applied to the nametag when a player sneaks (-128 to 127). -1 = fully opaque (255).")
        private int sneakOpacity = 70;

        @Comment("Whether to see the NameTag of a user only while pointing at them.")
        private boolean showWhileLooking = false;

        @Comment("Whether to see your own NameTag (similar to nametag mod of Lunar Client).")
        private boolean showCurrentNameTag = false;

        @Setter
        @Comment("When showCurrentNameTag is false, players may still enable seeing their own nametag via /unt preferences if they have permission.")
        private boolean allowPerPlayerShowOwnWhenGlobalDisabled = false;

        @Comment({
                "How to handle nametag visibility when there is no direct line of sight between players.",
                "Modes:",
                " - SEE_THROUGH: Nametags are fully visible through walls (vanilla behavior).",
                " - OBSCURED: Nametags are visible through walls but with reduced opacity (dimmed).",
                " - HIDE: Nametags are completely hidden and despawned behind walls (anti-wallhack)."
        })
        private ThroughWallMode throughWallMode = ThroughWallMode.SEE_THROUGH;

        @Comment("Settings applied when throughWallMode is set to OBSCURED or HIDE.")
        private ThroughWallSettings throughWallSettings = new ThroughWallSettings();
    }

    @Configuration
    @Getter
    @Setter
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class ThroughWallSettings {

        @Comment("Text opacity when the viewer has no clear line of sight and mode is OBSCURED (-128 to 127).")
        private int opacity = 55;

        @Comment("Maximum distance (blocks) from viewer to owner for through-walls checks.")
        private double maxDistance = 48.0;

        @Comment("How often to re-check line of sight on the main/region thread (server tick interval).")
        private int checkInterval = 5;
    }

    public enum ThroughWallMode {
        SEE_THROUGH,
        OBSCURED,
        HIDE
    }

    @Configuration
    @Getter
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class Performance {

        @Comment({
                "Whether to cache components for some time and reuse them.",
                "Useful for performance when a lot of gradients are used, but uses more memory.",
                "May cause problems with MiniPlaceholders, so disabled by default."
        })
        private boolean componentCaching = false;

        @Comment({
                "Default placeholder cache time in ticks. All placeholders without a specific rate use this.",
                "Effective refresh rate per placeholder = max(behavior.taskInterval, this value or its placeholderUpdateRates entry)."
        })
        private int placeholderCacheTime = 1;

        @Comment("Enable relational placeholders (per-viewer PAPI evaluation). Requires PlaceholderAPI.")
        private boolean enableRelationalPlaceholders = false;

        @Comment({
                "Per-placeholder refresh rate in ticks. The placeholder is re-fetched from PAPI at most once every",
                "max(behavior.taskInterval, value) ticks. Useful to slow down expensive but slowly-changing placeholders.",
                "Example:  \"%vault_eco_balance%\": 100   (re-fetches balance at most every 100 ticks)"
        })
        private Map<String, Integer> placeholderUpdateRates = new LinkedHashMap<>();

        @Comment({
                "Distance-aware refresh culling for the periodic nametag placeholder refresh task.",
                "Nearest viewers keep behavior.taskInterval; farther owners still refresh, but less often."
        })
        private DistanceRefreshCulling distanceRefreshCulling = new DistanceRefreshCulling();
    }

    @Configuration
    @Getter
    @Setter
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class DistanceRefreshCulling {

        @Comment("Enable distance-aware refresh intervals for far-away nametags.")
        private boolean enabled = true;

        @Comment("Distance in blocks up to which nametags use behavior.taskInterval.")
        private double nearDistance = 24.0;

        @Comment("Distance in blocks at/after which nametags use maxInterval.")
        private double maxDistance = 96.0;

        @Comment("Slowest refresh interval in ticks for far-away/no-viewer nametags.")
        private int maxInterval = 100;

        @Comment("Curve exponent for interval growth. 1 = linear, 2 = gentle near / stronger far.")
        private double curve = 2.0;
    }

    // ─── Nested types ─────────────────────────────────────────────────────────

    public record PlaceholderReplacement(String placeholder, String replacement) {
    }

    public record NameTag(@Nullable String permission, @NotNull List<DisplayGroup> displayGroups) {

        public NameTag(@NotNull final List<DisplayGroup> displayGroups) {
            this(null, displayGroups);
        }

        @NotNull
        public NameTag withPermission(@Nullable final String permission) {
            return new NameTag(permission, displayGroups);
        }

        @NotNull
        public NameTag withDisplayGroups(@NotNull final List<DisplayGroup> displayGroups) {
            return new NameTag(permission, displayGroups);
        }

        @NotNull
        public NameTag withDisplayGroups(@NotNull final UnaryOperator<DisplayGroup> unaryOperator) {
            return withDisplayGroups(displayGroups.stream().map(unaryOperator::apply).toList());
        }

        @NotNull
        public NameTag withBackground(@NotNull Background background) {
            return withDisplayGroups(group -> group.withBackground(background));
        }

        @NotNull
        public NameTag withScale(float scale) {
            return withDisplayGroups(group -> group.withScale(scale));
        }
    }

    public record NametagLine(@NotNull String text, @Nullable String when) {

        public NametagLine {
            text = text == null ? "" : text;
        }

        @NotNull
        public static NametagLine plain(@NotNull final String text) {
            return new NametagLine(text, null);
        }
    }

    /**
     * One stacked nametag display (text, item, or block). Visibility is controlled by optional group {@code when} (JEXL)
     * and optional {@link NametagLine#when()} for each text line.
     * <p>
     * {@code background} may be omitted in YAML (null): same as a disabled transparent background.
     * <p>
     * Optional {@code animation} (rotate, bob, dvd_bounce, pulse_scale, wiggle, orbit); optional {@code animation_interval}
     * overrides root {@code displayAnimationInterval}. Optional {@code billboard} overrides
     * {@code behavior.defaultBillboard} for this row only.
     * <p>
     * Optional {@code glow} (fixed, reference, rainbow, gradient); optional {@code glowInterval} overrides
     * root {@code displayAnimationInterval} for glow tick cadence.
     */
    public record DisplayGroup(
            List<NametagLine> lines,
            @Nullable Background background,
            float scale,
            float yOffset,
            @Nullable String when,
            boolean relationalConditions,
            @Nullable NametagDisplayType displayType,
            @Nullable String itemMaterial,
            @Nullable String blockMaterial,
            @Nullable String itemDisplayMode,
            @Nullable DisplayAnimation animation,
            @Nullable Integer animationInterval,
            @Nullable AbstractDisplayMeta.BillboardConstraints billboard,
            @Nullable GlowOverride glow,
            @Nullable Integer glowInterval) {

        public DisplayGroup {
            final NametagDisplayType resolved = displayType != null ? displayType : NametagDisplayType.TEXT;
            if (resolved != NametagDisplayType.TEXT) {
                lines = List.of();
            } else {
                lines = lines == null ? List.of() : lines.stream()
                        .map(line -> line == null ? NametagLine.plain("") : line)
                        .toList();
            }
            background = isRedundantOmittedBackground(background) ? null : background;
        }

        /**
         * Disabled background with black color and zero opacity — same as omitting {@code background} in YAML.
         * {@code seeThrough} is ignored for equivalence; the effective omitted style uses {@link #OMITTED_BACKGROUND}.
         */
        public static boolean isRedundantOmittedBackground(@Nullable final Background background) {
            if (background == null) return true;
            return !background.enabled() && background.opacity() == 0 && !background.shadowed();
        }

        private static final Background OMITTED_BACKGROUND = new Background(false, "#000000", 0, false, true);

        @NotNull
        public NametagDisplayType resolvedDisplayType() {
            return displayType != null ? displayType : NametagDisplayType.TEXT;
        }

        /**
         * Background from config, or a fixed disabled/transparent default when omitted.
         */
        @NotNull
        public Background effectiveBackground() {
            return background != null ? background : OMITTED_BACKGROUND;
        }

        /**
         * Scale for the display entity; values {@code <= 0} are treated as {@code 1}.
         */
        public float effectiveScale() {
            return scale > 0f ? scale : 1f;
        }

        /**
         * Ticks between animation pose updates for this row.
         */
        public int effectiveAnimationTickInterval(@NotNull Settings settings) {
            if (animationInterval != null && animationInterval > 0) {
                return animationInterval;
            }
            return Math.max(1, settings.getBehavior().resolveDisplayAnimationTickInterval());
        }

        @NotNull
        public AbstractDisplayMeta.BillboardConstraints effectiveBillboard(@NotNull Settings settings) {
            return billboard != null ? billboard : settings.getBehavior().getDefaultBillboard();
        }

        /**
         * Resolved glow from config (dereferences {@code type: reference}).
         */
        @Nullable
        public GlowOverride effectiveGlow(@NotNull Settings settings) {
            if (glow == null) {
                return null;
            }
            return glow.resolve(settings);
        }

        /**
         * Ticks between glow color updates for this row.
         */
        public int effectiveGlowTickInterval(@NotNull Settings settings) {
            if (glowInterval != null && glowInterval > 0) {
                return glowInterval;
            }
            return Math.max(1, settings.getBehavior().resolveDisplayAnimationTickInterval());
        }

        // ─── with* helpers ────────────────────────────────────────────────────

        public DisplayGroup withBackground(@Nullable Background background) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        public DisplayGroup withScale(float scale) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        @NotNull
        public DisplayGroup withAnimation(@Nullable DisplayAnimation animation) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        public DisplayGroup withLines(@NotNull List<NametagLine> lines) {
            return new DisplayGroup(lines, background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        public DisplayGroup withWhen(@Nullable String when) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        public DisplayGroup withBillboard(@Nullable AbstractDisplayMeta.BillboardConstraints billboard) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        public DisplayGroup withYOffset(float yOffset) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        @NotNull
        public DisplayGroup withGlow(@Nullable GlowOverride glow) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        @NotNull
        public DisplayGroup withGlowInterval(@Nullable Integer glowInterval) {
            return copy(background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        private DisplayGroup copy(
                @Nullable Background background,
                float scale,
                float yOffset,
                @Nullable String when,
                boolean relationalConditions,
                @Nullable NametagDisplayType displayType,
                @Nullable String itemMaterial,
                @Nullable String blockMaterial,
                @Nullable String itemDisplayMode,
                @Nullable DisplayAnimation animation,
                @Nullable Integer animationInterval,
                @Nullable AbstractDisplayMeta.BillboardConstraints billboard,
                @Nullable GlowOverride glow,
                @Nullable Integer glowInterval) {
            return new DisplayGroup(lines, background, scale, yOffset, when, relationalConditions, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
        }

        // ─── Builder ──────────────────────────────────────────────────────────

        @NotNull
        public static Builder builder() {
            return new Builder();
        }

        @NotNull
        public static Builder builder(@NotNull DisplayGroup base) {
            return new Builder(base);
        }

        public static final class Builder {
            private List<NametagLine> lines = new ArrayList<>();
            private @Nullable Background background = null;
            private float scale = 1.0f;
            private float yOffset = 1.0f;
            private @Nullable String when = null;
            private boolean relationalConditions = false;
            private @Nullable NametagDisplayType displayType = null;
            private @Nullable String itemMaterial = null;
            private @Nullable String blockMaterial = null;
            private @Nullable String itemDisplayMode = null;
            private @Nullable DisplayAnimation animation = null;
            private @Nullable Integer animationInterval = null;
            private @Nullable AbstractDisplayMeta.BillboardConstraints billboard = null;
            private @Nullable GlowOverride glow = null;
            private @Nullable Integer glowInterval = null;

            private Builder() {
            }

            private Builder(@NotNull DisplayGroup base) {
                this.lines = new ArrayList<>(base.lines());
                this.background = base.background();
                this.scale = base.scale();
                this.yOffset = base.yOffset();
                this.when = base.when();
                this.relationalConditions = base.relationalConditions();
                this.displayType = base.displayType();
                this.itemMaterial = base.itemMaterial();
                this.blockMaterial = base.blockMaterial();
                this.itemDisplayMode = base.itemDisplayMode();
                this.animation = base.animation();
                this.animationInterval = base.animationInterval();
                this.billboard = base.billboard();
                this.glow = base.glow();
                this.glowInterval = base.glowInterval();
            }

            public Builder line(@NotNull String text) {
                lines.add(NametagLine.plain(text));
                return this;
            }

            public Builder line(@NotNull String text, @Nullable String when) {
                lines.add(new NametagLine(text, when));
                return this;
            }

            public Builder lines(@NotNull List<NametagLine> lines) {
                this.lines = new ArrayList<>(lines);
                return this;
            }

            public Builder background(@Nullable Background bg) {
                this.background = bg;
                return this;
            }

            public Builder scale(float scale) {
                this.scale = scale;
                return this;
            }

            public Builder yOffset(float yOffset) {
                this.yOffset = yOffset;
                return this;
            }

            public Builder when(@Nullable String condition) {
                this.when = condition;
                return this;
            }

            public Builder relational() {
                this.relationalConditions = true;
                return this;
            }

            public Builder displayType(@Nullable NametagDisplayType type) {
                this.displayType = type;
                return this;
            }

            public Builder itemMaterial(@Nullable String mat) {
                this.itemMaterial = mat;
                return this;
            }

            public Builder blockMaterial(@Nullable String mat) {
                this.blockMaterial = mat;
                return this;
            }

            public Builder itemDisplayMode(@Nullable String mode) {
                this.itemDisplayMode = mode;
                return this;
            }

            public Builder animation(@Nullable DisplayAnimation anim) {
                this.animation = anim;
                return this;
            }

            public Builder animationInterval(int ticks) {
                this.animationInterval = ticks;
                return this;
            }

            public Builder billboard(@Nullable AbstractDisplayMeta.BillboardConstraints billboard) {
                this.billboard = billboard;
                return this;
            }

            public Builder glow(@Nullable GlowOverride glow) {
                this.glow = glow;
                return this;
            }

            public Builder glowInterval(int ticks) {
                this.glowInterval = ticks;
                return this;
            }

            @NotNull
            public DisplayGroup build() {
                return new DisplayGroup(List.copyOf(lines), background, scale, yOffset, when, relationalConditions,
                        displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard, glow, glowInterval);
            }
        }
    }

    /**
     * Nametag background. Use {@code color: "#RRGGBB"} for hex or {@code color: "R,G,B"} for RGB.
     * No {@code type:} discriminator needed.
     */
    @Configuration
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    @SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
    public static class Background {

        private boolean enabled = false;

        @Comment("Hex (#RRGGBB) or RGB (R,G,B) color string.")
        private String color = "#000000";

        private int opacity = 255;
        private boolean shadowed = false;
        private boolean seeThrough = false;

        // ─── Static factories ─────────────────────────────────────────────────

        @NotNull
        public static Background ofRGB(boolean enabled, int r, int g, int b, int opacity, boolean shadowed, boolean seeThrough) {
            return new Background(enabled, r + "," + g + "," + b, opacity, shadowed, seeThrough);
        }

        @NotNull
        public static Background ofHex(boolean enabled, @NotNull String hex, int opacity, boolean shadowed, boolean seeThrough) {
            return new Background(enabled, hex, opacity, shadowed, seeThrough);
        }

        @NotNull
        public static Background disabled() {
            return new Background(false, "#000000", 0, false, false);
        }

        // ─── Color parsing ────────────────────────────────────────────────────

        @NotNull
        public RgbaColor getRgba() {
            if (!enabled) {
                return RgbaColor.transparent();
            }
            try {
                if (color != null && color.startsWith("#")) {
                    return RgbaColor.fromHexRgb(color, opacity);
                }
                final String[] p = (color != null ? color : "0,0,0").split(",");
                if (p.length < 3) {
                    throw new IllegalArgumentException("expected R,G,B");
                }
                return RgbaColor.fromRgb(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        opacity);
            } catch (Exception e) {
                return RgbaColor.transparent();
            }
        }

        public int getArgb() {
            return getRgba().argb();
        }

        // ─── Immutable-style updates ──────────────────────────────────────────

        @NotNull
        public Background withEnabled(boolean v) {
            return new Background(v, color, opacity, shadowed, seeThrough);
        }

        @NotNull
        public Background withColor(@NotNull String v) {
            return new Background(enabled, v, opacity, shadowed, seeThrough);
        }

        @NotNull
        public Background withOpacity(int v) {
            return new Background(enabled, color, v, shadowed, seeThrough);
        }

        @NotNull
        public Background withShadowed(boolean v) {
            return new Background(enabled, color, opacity, v, seeThrough);
        }

        @NotNull
        public Background withSeeThrough(boolean v) {
            return new Background(enabled, color, opacity, shadowed, v);
        }
    }
}

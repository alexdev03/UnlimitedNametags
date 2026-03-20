package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Polymorphic;
import de.exlll.configlib.PolymorphicTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Configuration
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@Getter
public class Settings {

    @Comment({
            "Schema version for settings.yml (managed by the plugin; do not lower).",
            "1 = flat NameTag (lines on tag), 2 = displayGroups / DisplayGroup (current)."
    })
    private int configVersion = SettingsConfigVersion.CURRENT;

    private Map<String, NameTag> nameTags = new LinkedHashMap<>() {{
        put("staffer", new NameTag("nametag.staffer", List.of(new DisplayGroup(List.of("%luckperms_prefix% %player_name% %luckperms_suffix%"),
                new IntegerBackground(false, 255, 0, 0, 255, true, false), 1f, 1.0f, null, null, null, null, null, null, null, null))));
        put("default", new NameTag("nametag.default", List.of(new DisplayGroup(List.of("%luckperms_prefix% %player_name% %luckperms_suffix%"),
                        new IntegerBackground(false, 255, 0, 0, 255, true, false), 1f, 1.0f, null, null, null, null, null, null, null, null),
                new DisplayGroup(List.of("Rich Player"), new HexBackground(false, "#ffffff", 255, false, false), 1f, 1.0f, "%vault_eco_balance% > 1000", null, null, null, null, null, null, null))));
    }};

    @Setter
    @Comment("The default billboard constraints for the nametag. CENTER, HORIZONTAL, VERTICAL, FIXED)")
    private AbstractDisplayMeta.BillboardConstraints defaultBillboard = AbstractDisplayMeta.BillboardConstraints.CENTER;

    public NameTag getNametag(final Player player) {
        return nameTags.entrySet().stream()
                .filter(entry -> entry.getValue().permission == null || player.hasPermission(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nameTags.get("default"));
    }

    protected Optional<NameTag> getDefaultNameTag() {
        return Optional.ofNullable(nameTags.get("default"));
    }

    private int taskInterval = 20;

    @Comment({
            "Ticks between display animation updates when a display group has no animation_interval.",
            "Use 0 to use the same interval as taskInterval (nametag placeholder refresh cadence)."
    })
    private int displayAnimationInterval = 1;

    @Comment(value = {"This is opacity that will be applied to the nametag when a player sneaks. So, the value is from -128 to 127. ",
            "Similar to the background, the text rendering is discarded when it is less than 26. Defaults to -1, which represents 255 and is completely opaque."})
    private int sneakOpacity = 70;
    private float yOffset = 0.3f;
    private float viewDistance = 60;

    @Comment("""
            Which text formatter to use (MINIMESSAGE, MINEDOWN, LEGACY or UNIVERSAL)\s
            Take note that UNIVERSAL is the most resource intensive but it supports all formatting options (except for MINEDOWN)\s
            
            (&x&0&8&4&c&f&bc LEGACY OF LEGACY - &#084cfbc LEGACY - &#084cfbc& MINEDOWN - <color:#084cfbc> MINIMESSAGE)""")
    @Setter
    private Formatter format = Formatter.MINIMESSAGE;

    @Comment("Whether to disable the default name tag or not.")
    private boolean disableDefaultNameTag = true;

    @Comment("This option works if disableDefaultNameTag is enabled. This will skip the team internal cache and will make all default nametags invisible. " +
            "The use case is when there is a npc from a plugin that doesn't use display entities / armorstands for nametags, and there is an online player with the name of the npc.")
    private boolean forceDisableDefaultNameTag = false;

    private boolean removeEmptyLines = true;

    @Comment("Whether to see the NameTag of a user only while pointing at them")
    private boolean showWhileLooking = false;

    @Comment({
            "When true, viewers without direct line of sight to the nametag owner (server-side ray trace) still see the text",
            "with reduced opacity within obscuredNametagMaxDistance blocks. Through-wall visibility uses seeThrough on the text display for those viewers.",
            "Runs on the server main thread every obscuredNametagCheckInterval ticks; keep the interval reasonable on large player counts."
    })
    private boolean obscuredNametagThroughWalls = false;

    @Comment("Text opacity when the viewer has no clear line of sight to the owner (-128–127; same semantics as sneakOpacity).")
    private int obscuredNametagOpacity = 55;

    @Comment("Maximum distance (blocks) from viewer to owner for obscured-through-walls styling; beyond this, full opacity / normal seeThrough apply.")
    @Setter
    private double obscuredNametagMaxDistance = 48.0;

    @Comment("How often to re-check line of sight for obscuredNametagThroughWalls (server tick interval).")
    @Setter
    private int obscuredNametagCheckInterval = 5;

    @Comment("Whether to see your own NameTag (Similar to nametag mod of Lunar Client)")
    private boolean showCurrentNameTag = false;

    @Comment("""
            Whether to cache components for some time and reuse them or not.
             This is useful for performance improvements where a lot of gradients are used, but it uses a bit more memory.
             It could create problems with MiniPlaceholders, so it is disabled by default.""")
    private boolean componentCaching = false;

    @Comment("How long to cache placeholders for (in ticks)")
    private int placeholderCacheTime = 1;

    private boolean enableRelationalPlaceholders = false;

    public float getViewDistance() {
        return viewDistance / 160;
    }

    /**
     * Global animation tick spacing when a {@link DisplayGroup} has no {@code animation_interval}.
     */
    public int resolveDisplayAnimationTickInterval() {
        if (displayAnimationInterval > 0) {
            return displayAnimationInterval;
        }
        return Math.max(1, taskInterval);
    }

    @Comment("Match PAPI output strings. Quote reserved YAML 1.1 words: use placeholder: \"Yes\" not Yes (otherwise they become booleans).")
    private Map<String, List<PlaceholderReplacement>> placeholdersReplacements = new LinkedHashMap<>() {{
        put("%advancedvanish_is_vanished%", List.of(new PlaceholderReplacement("Yes", " &7[V]&r"), new PlaceholderReplacement("No", "")));
    }};


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

    /**
     * One stacked nametag display (text, item, or block). Visibility is controlled only by optional {@code when} (JEXL).
     * <p>
     * {@code background} may be omitted in YAML (null): same as a disabled transparent background — useful for {@link NametagDisplayType#ITEM}
     * and {@link NametagDisplayType#BLOCK} rows where text styling does not apply.
     * <p>
     * <b>Lines</b>: used only for {@link NametagDisplayType#TEXT} (default). For {@link NametagDisplayType#ITEM} / {@link NametagDisplayType#BLOCK},
     * {@code lines} in YAML are ignored; set {@code itemMaterial} / {@code blockMaterial} (required for content).
     * <p>
     * Optional {@code animation} (rotate, bob, dvd_bounce, pulse_scale, wiggle, orbit); optional {@code animation_interval}
     * (ticks between pose updates) overrides root {@code displayAnimationInterval} (or {@code taskInterval} when that is 0).
     * Optional {@code billboard} ({@code CENTER}, {@code HORIZONTAL}, {@code VERTICAL}, {@code FIXED}) overrides {@link Settings#getDefaultBillboard()} for this row only.
     */
    public record DisplayGroup(
            List<String> lines,
            @Nullable Background background,
            float scale,
            float yOffset,
            @Nullable String when,
            @Nullable NametagDisplayType displayType,
            @Nullable String itemMaterial,
            @Nullable String blockMaterial,
            @Nullable String itemDisplayMode,
            @Nullable DisplayAnimation animation,
            @Nullable Integer animationInterval,
            @Nullable AbstractDisplayMeta.BillboardConstraints billboard) {

        public DisplayGroup {
            final NametagDisplayType resolved = displayType != null ? displayType : NametagDisplayType.TEXT;
            if (resolved != NametagDisplayType.TEXT) {
                lines = List.of();
            } else {
                lines = lines == null ? List.of() : List.copyOf(lines);
            }
            background = isRedundantOmittedStyleIntegerBackground(background) ? null : background;
        }

        /**
         * Disabled integer RGB(0,0,0) with zero opacity and no shadow — same as omitting {@code background} in YAML
         * ({@code seeThrough} is ignored for equivalence; effective omitted style uses {@link #OMITTED_BACKGROUND}).
         */
        public static boolean isRedundantOmittedStyleIntegerBackground(@Nullable final Background background) {
            if (!(background instanceof IntegerBackground ib)) {
                return false;
            }
            return !ib.enabled()
                    && ib.opacity() == 0
                    && !ib.shadowed()
                    && ib.getRed() == 0
                    && ib.getGreen() == 0
                    && ib.getBlue() == 0;
        }

        private static final Background OMITTED_BACKGROUND = new IntegerBackground(false, 0, 0, 0, 0, false, true);

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
         * Scale for the display entity; values {@code <= 0} are treated as {@code 1} (zero scale makes the entity invisible).
         */
        public float effectiveScale() {
            return scale > 0f ? scale : 1f;
        }

        /**
         * Ticks between animation pose updates for this row; {@code null} or {@code <= 0} uses {@link Settings#resolveDisplayAnimationTickInterval()}.
         */
        public int effectiveAnimationTickInterval(@NotNull Settings settings) {
            if (animationInterval != null && animationInterval > 0) {
                return animationInterval;
            }
            return Math.max(1, settings.resolveDisplayAnimationTickInterval());
        }

        @NotNull
        public AbstractDisplayMeta.BillboardConstraints effectiveBillboard(@NotNull Settings settings) {
            return billboard != null ? billboard : settings.getDefaultBillboard();
        }

        public DisplayGroup withBackground(@NotNull Background background) {
            return new DisplayGroup(lines, background, scale, yOffset, when, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard);
        }

        public DisplayGroup withScale(float scale) {
            return new DisplayGroup(lines, background, scale, yOffset, when, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard);
        }

        @NotNull
        public DisplayGroup withAnimation(@Nullable DisplayAnimation animation) {
            return new DisplayGroup(lines, background, scale, yOffset, when, displayType, itemMaterial, blockMaterial, itemDisplayMode, animation, animationInterval, billboard);
        }

    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    @Polymorphic
    @PolymorphicTypes({
            @PolymorphicTypes.Type(type = IntegerBackground.class, alias = "integer"),
            @PolymorphicTypes.Type(type = HexBackground.class, alias = "hex")
    })
    @Configuration
    public static class Background {

        protected boolean enabled;
        protected int opacity;
        protected boolean shadowed;
        protected boolean seeThrough;

        public Color getColor() {
            return Color.BLACK.setAlpha(0);
        }
    }

    @Getter
    @NoArgsConstructor
    public static class IntegerBackground extends Background {

        private int red;
        private int green;
        private int blue;

        public IntegerBackground(final boolean enabled, final int red, final int green, final int blue, final int opacity, final boolean shadowed, final boolean seeThrough) {
            super(enabled, opacity, shadowed, seeThrough);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        @Override
        public Color getColor() {
            return !enabled() ? Color.BLACK.setAlpha(0) : Color.fromRGB(red, green, blue).setAlpha(opacity());
        }

        @NotNull
        public IntegerBackground clone() {
            return new IntegerBackground(enabled(), getRed(), getGreen(), getBlue(), opacity(), shadowed(), seeThrough());
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HexBackground extends Background {

        private String hex;

        public HexBackground(final boolean enabled, final String hex, final int opacity, final boolean shadowed, final boolean seeThrough) {
            super(enabled, opacity, shadowed, seeThrough);
            this.hex = hex;
        }

        @Override
        public Color getColor() {
            final int hex = Integer.parseInt(this.hex.substring(1), 16);
            return !enabled() ? Color.BLACK.setAlpha(0) : Color.fromRGB(hex).setAlpha(opacity());
        }

        @NotNull
        public HexBackground clone() {
            return new HexBackground(enabled(), getHex(), opacity(), shadowed(), seeThrough());
        }
    }
}

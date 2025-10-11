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
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Configuration
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@Getter
public class Settings {

    private Map<String, NameTag> nameTags = new LinkedHashMap<>() {{
        put("staffer", new NameTag("nametag.staffer", List.of(new LinesGroup(List.of("%luckperms_prefix% %player_name% %luckperms_suffix%"), List.of(new GlobalModifier(true)))),
                new IntegerBackground(false, 255, 0, 0, 255, true, false), 1f));
        put("default", new NameTag("nametag.default", List.of(new LinesGroup(List.of("%luckperms_prefix% %player_name% %luckperms_suffix%"), List.of(new GlobalModifier(true))),
                new LinesGroup(List.of("Rich Player"), List.of(new ConditionalModifier("%vault_eco_balance%", ">", "1000")))),
                new HexBackground(false, "#ffffff", 255, false, false), 1f));
    }};

    @Setter
    @Comment("The default billboard constraints for the nametag. CENTER, HORIZONTAL, VERTICAL, FIXED)")
    private AbstractDisplayMeta.BillboardConstraints defaultBillboard = AbstractDisplayMeta.BillboardConstraints.CENTER;

    public NameTag getNametag(Player player) {
        return nameTags.entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nameTags.get("default"));
    }

    protected Optional<NameTag> getDefaultNameTag() {
        return Optional.ofNullable(nameTags.get("default"));
    }

    private int taskInterval = 20;

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

    private Map<String, List<PlaceholderReplacement>> placeholdersReplacements = new LinkedHashMap<>() {{
        put("%advancedvanish_is_vanished%", List.of(new PlaceholderReplacement("Yes", " &7[V]&r"), new PlaceholderReplacement("No", "")));
    }};


    public record PlaceholderReplacement(String placeholder, String replacement) {
    }

    public record NameTag(String permission, List<LinesGroup> linesGroups, Background background, float scale) {
    }

    public record LinesGroup(List<String> lines, List<Modifier> modifiers) {

    }

    @Getter
    @NoArgsConstructor
    @Accessors(fluent = true)
    @Polymorphic
    @PolymorphicTypes({
            @PolymorphicTypes.Type(type = GlobalModifier.class, alias = "global"),
            @PolymorphicTypes.Type(type = ConditionalModifier.class, alias = "conditional")
    })
    @Configuration
    public abstract static class Modifier {

        public abstract boolean isVisible(@NotNull Player player, @NotNull UnlimitedNameTags plugin);

    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GlobalModifier extends Modifier {

        private boolean enabled;

        @Override
        public boolean isVisible(@NotNull Player player, @NotNull UnlimitedNameTags plugin) {
            return enabled;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionalModifier extends Modifier {

        private String parameter;
        private String condition;
        private String value;

        public String getExpression() {
            return parameter + " " + condition + " " + value;
        }

        @Override
        public boolean isVisible(@NotNull Player player, @NotNull UnlimitedNameTags plugin) {
            return plugin.getConditionalManager().evaluateExpression(this, player);
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

        public IntegerBackground(boolean enabled, int red, int green, int blue, int opacity, boolean shadowed, boolean seeThrough) {
            super(enabled, opacity, shadowed, seeThrough);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        @Override
        public Color getColor() {
            return !enabled ? Color.BLACK.setAlpha(0) : Color.fromRGB(red, green, blue).setAlpha(opacity);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HexBackground extends Background {

        private String hex;

        public HexBackground(boolean enabled, String hex, int opacity, boolean shadowed, boolean seeThrough) {
            super(enabled, opacity, shadowed, seeThrough);
            this.hex = hex;
        }

        @Override
        public Color getColor() {
            int hex = Integer.parseInt(this.hex.substring(1), 16);
            return !enabled ? Color.BLACK.setAlpha(0) : Color.fromRGB(hex).setAlpha(opacity);
        }
    }
}

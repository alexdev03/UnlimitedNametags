package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Polymorphic;
import de.exlll.configlib.PolymorphicTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@Configuration
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
@Getter
public class Settings {

    private Map<String, NameTag> nameTags = Map.of(
            "staffer", new NameTag("nametag.staffer", List.of("%luckperms_prefix% %player_name% %luckperms_suffix%"),
                    new IntegerBackground(true, 255, 0, 0, 255, true, false)),
            "default", new NameTag("nametag.default", List.of("%luckperms_prefix% %player_name% %luckperms_suffix%", "%vault_eco_balance_formatted%"),
                    new HexBackground(false, "#ffffff", 255, false, false))
    );

    public NameTag getNametag(Player player) {
        return nameTags.entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nameTags.get("default"));
    }

    private int taskInterval = 20;

    @Comment(value = {"This is opacity that will be applied to the nametag when a player sneaks. So, the value is from -128 to 127. ",
            "Similar to the background, the text rendering is discarded when it is less than 26. Defaults to -1, which represents 255 and is completely opaque."})
    private int sneakOpacity = 70;
    private float yOffset = 0.3f;
    private float viewDistance = 60;

    @Comment("""
            Which text formatter to use (MINEDOWN, MINIMESSAGE, LEGACY or UNIVERSAL)\s
            Take note that UNIVERSAL is the most resource intensive but it supports all formatting options. \

            (&x&0&8&4&c&f&bc LEGACY OF LEGACY - &#084cfbc LEGACY - &#084cfbc& MINEDOWN - <color:#084cfbc> MINIMESSAGE)""")
    private Formatter format = Formatter.LEGACY;

    @Comment("Whether to disable the default name tag or not.")
    private boolean disableDefaultNameTag = false;

    @Comment("Whether to disable the default name tag or not for bedrock players. Only works if Floodgate is installed.")
    private boolean disableDefaultNameTagBedrock = false;

    private boolean removeEmptyLines = true;

    public float getViewDistance() {
        return viewDistance / 160;
    }

    public record NameTag(String permission, List<String> lines, Background background) {
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

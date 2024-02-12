package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
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
                    new Background(true, 255, 0, 0, 255, true, false)),
            "default", new NameTag("nametag.default", List.of("%luckperms_prefix% %player_name% %luckperms_suffix%", "%vault_eco_balance_formatted%"),
                    new Background(false, 0, 0, 0, 255, false, false))
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
    private float yOffset = 0.7f;
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

    public record Background(boolean enabled, int red, int green, int blue, int opacity, boolean shadowed,
                             boolean seeThrough) {

        public Color getColor() {
            return !enabled ? Color.BLACK.setAlpha(0) : Color.fromRGB(red, green, blue).setAlpha(opacity);
        }

    }
}

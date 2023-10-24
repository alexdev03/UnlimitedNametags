package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@Configuration
@SuppressWarnings("FieldMayBeFinal")
public class Settings {

    private Map<String, NameTag> nameTags = Map.of(
            "staffer", new NameTag("nametag.staffer", List.of("%prefix% %username% %suffix%"), 1),
            "default", new NameTag("nametag.default", List.of("%prefix% %username% %suffix%", "%money%"), 1)
    );

    public NameTag getNametag(Player player) {
        return nameTags.entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nameTags.get("default"));
    }

    @Getter
    private int taskInterval = 20;

    @Comment(value = {"This is opacity that will be applied to the nametag when a player sneaks. So, the value is from -128 to 127. ",
            "Similar to the background, the text rendering is discarded when it is less than 26. Defaults to -1, which represents 255 and is completely opaque."})
    @Getter
    private int sneakOpacity = 70;


    public record NameTag(String permission, List<String> lines, int space) {
    }
}

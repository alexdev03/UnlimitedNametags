package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Configuration;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

@Configuration
public class Settings {


    private Map<String, Nametag> nametags = Map.of(
            "staffer", new Nametag("nametag.staffer", List.of("%prefix% %username% %suffix%")),
            "default", new Nametag("nametag.default", List.of("%prefix% %username% %suffix%", "%money%"))
    );

    public Nametag getNametag(Player player) {
        return nametags.entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getValue().permission))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(nametags.get("default"));
    }

    @Getter
    private int taskInterval = 20;




    public record Nametag(String permission, List<String> lines) {
    }
}

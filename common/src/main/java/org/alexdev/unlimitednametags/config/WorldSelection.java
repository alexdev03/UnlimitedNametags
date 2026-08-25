package org.alexdev.unlimitednametags.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Selects the worlds where UnlimitedNameTags is active. */
@Configuration
@Getter
@Setter
@NoArgsConstructor
public class WorldSelection {

    @Comment({
            "BLACKLIST: UNT is active in every world except the worlds listed below.",
            "WHITELIST: UNT is active only in the worlds listed below."
    })
    private Mode mode = Mode.BLACKLIST;

    @Comment("World names are matched exactly against Bukkit world names.")
    private List<String> list = new ArrayList<>();

    public WorldSelection(Mode mode, List<String> list) {
        this.mode = mode;
        this.list = new ArrayList<>(list);
    }

    public boolean isEnabled(String worldName) {
        final boolean listed = list != null && list.contains(worldName);
        return mode == Mode.WHITELIST ? listed : !listed;
    }

    public enum Mode {
        BLACKLIST,
        WHITELIST
    }
}

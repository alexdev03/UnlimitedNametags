package org.alexdev.unlimitednametags.nametags;

import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
import org.jetbrains.annotations.NotNull;

final class CompactStackVisibility {

    private CompactStackVisibility() {
    }

    static boolean shouldReserveTextRowStackSpace(@NotNull Settings.DisplayGroup displayGroup) {
        return displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT
                && displayGroup.lines().stream().anyMatch(line -> !line.text().isBlank());
    }
}

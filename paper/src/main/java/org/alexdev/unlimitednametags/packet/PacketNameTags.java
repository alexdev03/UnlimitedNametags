package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PacketNameTags {

    private PacketNameTags() {
    }

    public static @NotNull PaperNametagRow create(
            @NotNull UnlimitedNameTags plugin,
            @NotNull Player owner,
            @NotNull Settings.DisplayGroup displayGroup) {
        return switch (displayGroup.resolvedDisplayType()) {
            case TEXT -> new PaperTextPacketNameTag(plugin, owner, displayGroup);
            case ITEM -> new PaperItemPacketNameTag(plugin, owner, displayGroup);
            case BLOCK -> new PaperBlockPacketNameTag(plugin, owner, displayGroup);
        };
    }
}

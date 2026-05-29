package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.BukkitNametagMaterialBridge;
import org.alexdev.unlimitednametags.platform.BukkitNametagPlatform;
import org.alexdev.unlimitednametags.platform.BukkitNametagRuntime;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PaperItemPacketNameTag extends ItemPacketNameTag implements PaperNametagRow {

    private final UnlimitedNameTags plugin;

    public PaperItemPacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner,
            @NotNull Settings.DisplayGroup displayGroup) {
        super(
                new BukkitNametagRuntime(plugin),
                new BukkitNametagPlatform(plugin, owner.getUniqueId()),
                new BukkitNametagMaterialBridge(plugin),
                owner.getUniqueId(),
                displayGroup);
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public UnlimitedNameTags getPlugin() {
        return plugin;
    }
}

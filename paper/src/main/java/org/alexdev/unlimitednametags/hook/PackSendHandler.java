package org.alexdev.unlimitednametags.hook;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.NotNull;

public interface PackSendHandler extends Listener {

    @NotNull
    UnlimitedNameTags getPlugin();

    @EventHandler
    default void onPackSend(PlayerResourcePackStatusEvent event) {
        if(PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_19_4)) {
            return;
        }

        if(event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            getPlugin().getNametagManager().refreshDisplaysForPlayer(event.getPlayer());
        }
    }

}

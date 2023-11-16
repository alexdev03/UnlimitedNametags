package org.alexdev.unlimitednametags.events;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent;

@RequiredArgsConstructor
public class PurpurListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler()
    public void onTeleportCancelled(EntityTeleportHinderedEvent e) {
        if (e.getReason() != EntityTeleportHinderedEvent.Reason.IS_VEHICLE) {
            return;
        }

        if(!(e.getEntity() instanceof Player player)) return;

        player.eject();
        plugin.getNametagManager().removePlayer(player);
        e.setShouldRetry(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportComplete(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            plugin.getNametagManager().addPlayer(event.getPlayer());
        }, 1);

    }
}

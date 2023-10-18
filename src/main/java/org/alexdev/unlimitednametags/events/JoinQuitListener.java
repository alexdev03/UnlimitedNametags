package org.alexdev.unlimitednametags.events;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNametags;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

@RequiredArgsConstructor
public class JoinQuitListener implements Listener {

    private final UnlimitedNametags plugin;


    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getNametagManager().addPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getNametagManager().removePlayer(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.getNametagManager().refreshPlayer(e.getPlayer());
    }


}

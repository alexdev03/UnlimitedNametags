package org.alexdev.unlimitednametags.events;

import org.alexdev.unlimitednametags.UnlimitedNametags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;


public class PlayerListener implements Listener {

    private final UnlimitedNametags plugin;

    public PlayerListener(UnlimitedNametags plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().addPlayer(event.getPlayer()), 5);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getNametagManager().removePlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        TextDisplay display = plugin.getNametagManager().removePassenger(event.getPlayer());

        Location location = event.getTo().clone();
        //add 1.80 to make a perfect tp animation
        location.setY(location.getY() + 1.80);

        display.teleport(location);

        //hide display for all players to prevent bad animation with tp
        Bukkit.getOnlinePlayers().forEach(p -> p.hideEntity(plugin, display));

        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().applyPassenger(event.getPlayer()), 2);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        plugin.getNametagManager().updateSneaking(event.getPlayer(), event.isSneaking());
    }


}

package org.alexdev.unlimitednametags.events;

import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;


public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().addPlayer(event.getPlayer()),
                5);

        plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getNametagManager().removePlayer(event.getPlayer());
    }

//    @EventHandler
//    public void onEject(EntityDismountEvent event){
//        if(!(event.getEntity() instanceof Player player)) return;
//        if(!(event.getDismounted() instanceof TextDisplay passenger)) return;
//
//        if(!passenger.hasMetadata("nametag")) return;
//        plugin.getNametagManager().removePassenger(player);
//    }

//    @EventHandler()
//    public void onTeleport(PlayerTeleportEvent event) {
////        final TextDisplay display = plugin.getNametagManager().removePassenger(event.getPlayer());
////
////        if (display == null) {
////            return;
////        }
////
////        final Location location = event.getTo().clone();
////        //add 1.80 to make a perfect tp animation
////        location.setY(location.getY() + 1.80);
////
////        display.teleport(location);
////
////        //hide display for all players to prevent bad animation with tp
////        Bukkit.getOnlinePlayers().forEach(p -> p.hideEntity(plugin, display));
////
////        Bukkit.getScheduler().runTaskLater(plugin,
////                () -> plugin.getNametagManager().applyPassenger(event.getPlayer()), 5);
//        plugin.getNametagManager().removePlayer(event.getPlayer());
//
//        Bukkit.getScheduler().runTaskLater(plugin, () -> {
//            if (!event.getPlayer().isOnline()) return;
//            plugin.getNametagManager().addPlayer(event.getPlayer());
//        }, 5);
//    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        plugin.getNametagManager().updateSneaking(event.getPlayer(), event.isSneaking());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().addPlayer(e.getPlayer());
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removePlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        if(!event.hasChangedPosition()) return;

        org.bukkit.util.BoundingBox playerBoundingBox = event.getPlayer().getBoundingBox();
        World world = event.getPlayer().getWorld();
        Location loc1 = new Location(world, playerBoundingBox.getMaxX(), playerBoundingBox.getMaxY(), playerBoundingBox.getMaxZ());
        Location loc2 = loc1.clone().subtract(0, 1, 0);
        Location loc3 = new Location(world, playerBoundingBox.getMinX(), playerBoundingBox.getMinY(), playerBoundingBox.getMinZ());
        Location loc4 = loc3.clone().add(0, 1, 0);

        boolean inPortal = false;
        for (Location loc : new Location[]{loc1, loc2, loc3, loc4}) {
            Block block = loc.getBlock();
            if (
                    block.getType() == Material.NETHER_PORTAL
                            || block.getType() == Material.END_PORTAL
                            || block.getType() == Material.END_GATEWAY
            ) {
                inPortal = true;
                break;
            }
        }

        if(inPortal) {
            event.getPlayer().eject();
            plugin.getNametagManager().removePlayer(event.getPlayer());
        } else {
            plugin.getNametagManager().addPlayer(event.getPlayer());
        }
    }


}

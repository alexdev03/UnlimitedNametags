package org.alexdev.unlimitednametags.events;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.util.Set;
import java.util.UUID;


public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;
    private final Set<UUID> justJoined;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.justJoined = Sets.newConcurrentHashSet();
    }

    public boolean isJustJoined(UUID uuid) {
        return justJoined.contains(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getNametagManager().addPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getNametagManager().removePlayer(event.getPlayer());
    }

//    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
//    public void onEject(EntityDismountEvent event) {
//        if (!(event.getEntity() instanceof TextDisplay textDisplay)) return;
//        if (!(event.getDismounted() instanceof Player player)) return;
//
//        if (!textDisplay.hasMetadata("nametag")) return;
//
//        if (ejectCache.contains(player.getUniqueId())) return;
//
//        if(plugin.getNametagManager().getEjectable().contains(player.getUniqueId())) return;
//
//        ejectCache.add(player.getUniqueId());
//        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
//            if (player.isOnline()) {
//                plugin.getNametagManager().teleportAndApply(player);
//            }
//        }, 3);
//    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportComplete(PlayerTeleportEvent event) {
        plugin.getNametagManager().teleportAndApply(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        plugin.getNametagManager().updateSneaking(event.getPlayer(), event.isSneaking());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().addPlayer(e.getPlayer());
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removePlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getNametagManager().removePlayer(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getNametagManager().addPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {

        if (!event.hasChangedPosition()) return;

        final org.bukkit.util.BoundingBox playerBoundingBox = event.getPlayer().getBoundingBox();
        final World world = event.getPlayer().getWorld();
        final Location loc1 = new Location(world, playerBoundingBox.getMaxX(), playerBoundingBox.getMaxY(), playerBoundingBox.getMaxZ());
        final Location loc2 = loc1.clone().subtract(0, 1, 0);
        final Location loc3 = new Location(world, playerBoundingBox.getMinX(), playerBoundingBox.getMinY(), playerBoundingBox.getMinZ());
        final Location loc4 = loc3.clone().add(0, 1, 0);

        boolean inPortal = false;
        for (final Location loc : new Location[]{loc1, loc2, loc3, loc4}) {
            final Block block = loc.getBlock();
            if (
                    block.getType() == Material.NETHER_PORTAL
                            || block.getType() == Material.END_PORTAL
                            || block.getType() == Material.END_GATEWAY
            ) {
                inPortal = true;
                break;
            }
        }

//        if (inPortal) {
//            event.getPlayer().eject();
//            plugin.getNametagManager().removePlayer(event.getPlayer());
//        } else {
//            plugin.getNametagManager().addPlayer(event.getPlayer());
//        }
    }


}

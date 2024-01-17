package org.alexdev.unlimitednametags.events;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;
    private final List<UUID> ejectCache;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.ejectCache = new ArrayList<>();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getNametagManager().addPlayer(event.getPlayer());

        plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getNametagManager().removePlayer(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEject(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof TextDisplay textDisplay)) return;
        if (!(event.getDismounted() instanceof Player player)) return;

        if (!textDisplay.hasMetadata("nametag")) return;

        if (ejectCache.contains(player.getUniqueId())) return;

        ejectCache.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> ejectCache.remove(player.getUniqueId()), 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getNametagManager().teleportAndApply(player);
            }
        }, 11);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportComplete(PlayerTeleportEvent event) {
        plugin.getNametagManager().teleportAndApply(event.getPlayer());
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
            plugin.getNametagManager().removePlayer(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onEntityDespawn(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof TextDisplay textDisplay) {
            plugin.getNametagManager().removeDeadDisplay(textDisplay);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getNametagManager().removePlayer(event.getEntity(), false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.getNametagManager().addPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {

        if (!event.hasChangedPosition()) return;

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

        if (inPortal) {
            event.getPlayer().eject();
            plugin.getNametagManager().removePlayer(event.getPlayer(), false);
        } else {
            plugin.getNametagManager().addPlayer(event.getPlayer());
        }
    }


}

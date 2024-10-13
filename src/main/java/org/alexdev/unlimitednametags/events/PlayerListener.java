package org.alexdev.unlimitednametags.events;

import com.github.Anon8281.universalScheduler.foliaScheduler.FoliaScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.viaversion.viaversion.api.Via;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final UnlimitedNameTags plugin;
    private final Set<UUID> diedPlayers;
    private final Map<Integer, UUID> playerEntityId;
    private final Map<UUID, Integer> protocolVersion;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.diedPlayers = Sets.newConcurrentHashSet();
        this.playerEntityId = Maps.newConcurrentMap();
        this.protocolVersion = Maps.newConcurrentMap();
        this.loadFoliaRespawnTask();
        this.loadEntityIds();
    }


    private void loadEntityIds() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            playerEntityId.put(player.getEntityId(), player.getUniqueId());
            protocolVersion.put(player.getUniqueId(), getProtocolVersion(player));
        });
    }

    private void loadFoliaRespawnTask() {
        if (!(plugin.getTaskScheduler() instanceof FoliaScheduler)) {
            return;
        }

        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> diedPlayers.forEach(player -> {
            final Player p = plugin.getServer().getPlayer(player);
            if (p == null || p.isDead()) {
                return;
            }
            plugin.getNametagManager().addPlayer(p);
            diedPlayers.remove(player);
        }), 1, 1);
    }

    public Optional<Player> getPlayerFromEntityId(int entityId) {
        final UUID player = playerEntityId.get(entityId);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugin.getServer().getPlayer(player));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        protocolVersion.put(event.getPlayer().getUniqueId(), getProtocolVersion(event.getPlayer()));
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().addPlayer(event.getPlayer()), 2);
        playerEntityId.put(event.getPlayer().getEntityId(), event.getPlayer().getUniqueId());
    }

    public int getProtocolVersion(@NotNull UUID player) {
        return protocolVersion.get(player);
    }

    private int getProtocolVersion(@NotNull Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            return Via.getAPI().getPlayerVersion(player.getUniqueId());
        }
        return PacketEvents.getAPI().getPlayerManager().getUser(player).getClientVersion().getProtocolVersion();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        diedPlayers.remove(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().removePlayer(event.getPlayer(), true), 1);
        playerEntityId.remove(event.getPlayer().getEntityId());
        protocolVersion.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPotion(@NotNull EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) {
            if (event.getNewEffect() == null || event.getNewEffect().getType() != PotionEffectType.INVISIBILITY) {
                return;
            }
            plugin.getNametagManager().removeAllViewers(player);
        } else if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (event.getOldEffect() == null || event.getOldEffect().getType() != PotionEffectType.INVISIBILITY) {
                return;
            }
            plugin.getNametagManager().showToTrackedPlayers(player, plugin.getTrackerManager().getTrackedPlayers().get(player.getUniqueId()));

        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().showToTrackedPlayers(e.getPlayer(), plugin.getTrackerManager().getTrackedPlayers().get(e.getPlayer().getUniqueId()));
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removeAllViewers(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        diedPlayers.add(event.getEntity().getUniqueId());
        plugin.getNametagManager().removeAllViewers(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        diedPlayers.remove(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            plugin.getNametagManager().getPacketDisplayText(event.getPlayer()).ifPresent(d -> d.setVisible(true));
        }, 1);
    }

    @EventHandler
    public void onPlayerVanishes(@NotNull PlayerShowEntityEvent event) {
        if(!(event.getEntity() instanceof Player player)) {
            return;
        }

        if(plugin.getConfigManager().getSettings().isShowWhileLooking()) {
            return;
        }

        plugin.getNametagManager().getPacketDisplayText(player).ifPresent(display -> {
            display.showToPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onPlayerUnvanishes(@NotNull PlayerHideEntityEvent event) {
        if(!(event.getEntity() instanceof Player player)) {
            return;
        }

        plugin.getNametagManager().getPacketDisplayText(player).ifPresent(display -> {
            display.hideFromPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        if(!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }

        if(event.getFrom().getWorld() == event.getTo().getWorld() && event.getFrom().distance(event.getTo()) <= 80) {
            return;
        }

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().updateDisplay(event.getPlayer(), event.getPlayer()), 5);
    }

}

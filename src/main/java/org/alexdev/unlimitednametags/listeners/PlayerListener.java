package org.alexdev.unlimitednametags.listeners;

import com.github.Anon8281.universalScheduler.foliaScheduler.FoliaScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.PackSendHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerListener implements PackSendHandler {

    @Getter
    private final UnlimitedNameTags plugin;
    private final Set<UUID> diedPlayers;
    private final Map<Integer, UUID> playerEntityId;
    @Getter
    private final Map<String, UUID> playerNameId;

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.diedPlayers = Sets.newConcurrentHashSet();
        this.playerEntityId = Maps.newConcurrentMap();
        this.playerNameId = Maps.newConcurrentMap();
        this.loadFoliaRespawnTask();
        this.loadEntityIds();
    }

    private void loadEntityIds() {
        Bukkit.getOnlinePlayers().forEach(player -> playerEntityId.put(player.getEntityId(), player.getUniqueId()));
    }

    private void loadFoliaRespawnTask() {
        if (!(plugin.getTaskScheduler() instanceof FoliaScheduler)) {
            return;
        }

        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> diedPlayers.forEach(died -> {
            final Player player = plugin.getServer().getPlayer(died);
            if (player == null || player.isDead()) {
                return;
            }

            plugin.getNametagManager().showToTrackedPlayers(player);
            diedPlayers.remove(died);
        }), 1, 1);
    }

    public Optional<Player> getPlayerFromEntityId(int entityId) {
        final UUID player = playerEntityId.get(entityId);
        if (player == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(plugin.getServer().getPlayer(player));
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        final String name = event.getPlayer().getName();
        playerNameId.put(name, event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            if (Bukkit.getPlayer(name) == null) {
                playerNameId.remove(name);
            }
        }, 40);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().addPlayer(event.getPlayer(), true), 6);
        playerEntityId.put(event.getPlayer().getEntityId(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.getPacketEventsListener().removePlayerData(event.getPlayer());
        playerNameId.remove(event.getPlayer().getName());
        diedPlayers.remove(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            plugin.getNametagManager().removePlayer(event.getPlayer());
            plugin.getNametagManager().clearCache(event.getPlayer().getUniqueId());
            plugin.getPlaceholderManager().removePlayer(event.getPlayer());
            diedPlayers.remove(event.getPlayer().getUniqueId());
        }, 1);
        playerEntityId.remove(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onPotion(@NotNull EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player is in spectator mode, skipping potion event");
            }
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) {
            if (event.getNewEffect() == null || (!event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY))) {
                if (plugin.getNametagManager().isDebug()) {
                    plugin.getLogger().info("Potion effect is not invisibility, skipping potion event");
                }
                return;
            }
            plugin.getNametagManager().removeAllViewers(player);
            plugin.getNametagManager().blockPlayer(player);
        } else if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (event.getOldEffect() == null || !event.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY)) {
                if (plugin.getNametagManager().isDebug()) {
                    plugin.getLogger().info("Potion effect is not invisibility, skipping potion event: " + (event.getOldEffect() != null ? event.getOldEffect().getType() : null));
                }
                return;
            }

            plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                plugin.getNametagManager().unblockPlayer(player);
                plugin.getNametagManager().showToTrackedPlayers(player, plugin.getTrackerManager().getTrackedPlayers().get(player.getUniqueId()));
            }, 3);

        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().unblockPlayer(e.getPlayer());
            plugin.getNametagManager().showToTrackedPlayers(e.getPlayer());
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().removeAllViewers(e.getPlayer()), 2);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        diedPlayers.add(event.getEntity().getUniqueId());
        plugin.getNametagManager().removeAllViewers(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        diedPlayers.remove(event.getPlayer().getUniqueId());
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            final List<UUID> nearbyPlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getWorld().equals(event.getPlayer().getWorld()) && p.getLocation().distanceSquared(event.getPlayer().getLocation()) <= 6400)
                    .map(Player::getUniqueId)
                    .toList();
            plugin.getNametagManager().showToTrackedPlayers(event.getPlayer(), nearbyPlayers);
        }, 4);
    }

    @EventHandler
    public void onPlayerVanishes(@NotNull PlayerShowEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking()) {
            return;
        }

        plugin.getNametagManager().getPacketDisplayText(player).ifPresent(display -> {
            display.showToPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onPlayerUnvanishes(@NotNull PlayerHideEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        plugin.getNametagManager().getPacketDisplayText(player).ifPresent(display -> {
            display.hideFromPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        //1.20.1+ bug
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_1) &&
                event.getFrom().getWorld() != event.getTo().getWorld()
        ) {
            plugin.getTrackerManager().forceUntrack(event.getPlayer());
        }
        if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }

        if (event.getFrom().getWorld() == event.getTo().getWorld() && event.getFrom().distance(event.getTo()) <= 80) {
            return;
        }

        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().showToOwner(event.getPlayer()), 5);
    }

    public void logicElytra(Player player) {
        if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }

        plugin.getNametagManager().getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.hideForOwner();

            plugin.getTaskScheduler().runTaskLaterAsynchronously(packetDisplayText::showForOwner, 5);
        });
    }

}

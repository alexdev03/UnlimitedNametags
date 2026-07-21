package org.alexdev.unlimitednametags.listeners;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.PackSendHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerListener implements PackSendHandler {

    @Getter
    private final UnlimitedNameTags plugin;
    private final Set<UUID> diedPlayers;
    private final Map<Integer, UUID> playerEntityId;
    @Getter
    private final Map<String, UUID> playerNameId;
    @Getter
    private final Map<UUID, Player> onlinePlayers;
    private final Map<UUID, MyScheduledTask> teleportSyncTasks;
    private final Map<UUID, UUID> teleportSyncRunIds;
    private final Map<UUID, UUID> zeroDamageRecoveryRunIds;
    private final Map<UUID, MyScheduledTask> respawnShowTasks;
    private final Map<UUID, Location> playerWorlds;
    private static final long[] TELEPORT_SYNC_DELAYS = {5L, 20L, 60L, 100L};

    public PlayerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.diedPlayers = Sets.newConcurrentHashSet();
        this.playerEntityId = Maps.newConcurrentMap();
        this.playerNameId = Maps.newConcurrentMap();
        this.onlinePlayers = Maps.newConcurrentMap();
        this.teleportSyncTasks = Maps.newConcurrentMap();
        this.teleportSyncRunIds = Maps.newConcurrentMap();
        this.zeroDamageRecoveryRunIds = Maps.newConcurrentMap();
        this.respawnShowTasks = Maps.newConcurrentMap();
        this.playerWorlds = Maps.newConcurrentMap();
        this.loadRespawnSafetyTask();
        this.loadFoliaTeleportEvent();
        this.loadEntityIds();
    }

    private void loadEntityIds() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            playerEntityId.put(player.getEntityId(), player.getUniqueId());
            onlinePlayers.put(player.getUniqueId(), player);
        });
    }

    /**
     * Safety net that every tick checks {@link #diedPlayers} and re-shows nametags for anyone who is no longer dead.
     * <p>
     * Covers edge cases where {@link PlayerRespawnEvent} never fires (e.g. revive plugins that restore health
     * directly, custom death flows) and same-tick death+respawn races where the scheduled show from
     * {@link #scheduleRespawnShow(Player)} may have been skipped or cancelled. Runs on both Paper and Folia.
     */
    private void loadRespawnSafetyTask() {
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> diedPlayers.forEach(died -> {
            final Player player = plugin.getServer().getPlayer(died);
            if (player == null || !player.isOnline()) {
                diedPlayers.remove(died);
                return;
            }
            if (player.isDead()) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                diedPlayers.remove(died);
                return;
            }

            diedPlayers.remove(died);
            cancelRespawnShow(died);
            plugin.getNametagManager().showToTrackedPlayers(player);
        }), 1, 1);
    }

    /**
     *
    * Folia does not fire {@link PlayerTeleportEvent} for async teleports, so we poll player locations every tick and detect world changes
     * or large distance changes to trigger the same logic as a teleport event.
    *
     */
    private void loadFoliaTeleportEvent() {
        if (!isFolia()) {
            return;
        }

        plugin.getLogger().info("Folia detected, using async teleport event for teleport sync");
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                final UUID uuid = player.getUniqueId();
                final Location lastLocation = playerWorlds.getOrDefault(uuid, player.getLocation());
                final Location currentLocation = player.getLocation();
                if (!lastLocation.getWorld().equals(currentLocation.getWorld())
                        || lastLocation.distanceSquared(currentLocation) > 64*64) {
                    handlePositionChange(player, lastLocation, currentLocation);
                }

                playerWorlds.put(uuid, currentLocation);
            }
        }, 1, 10);
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Optional<Player> getPlayerFromEntityId(int entityId) {
        final UUID player = playerEntityId.get(entityId);
        if (player == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(plugin.getServer().getPlayer(player));
    }

    /**
     * Runs first so PDC-backed preference flags are in memory before tracker / entity events.
     * Tick 1: packet hide for {@code seeothers false}. Tick 12: once {@code addPlayer} has usually
     * created rows, packet fix for hide-own-from-others and hide-own-from-self.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoinSyncPreferences(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.getNametagManager().syncPlayerPreferenceSetsFromPdc(player);
        plugin.getNametagManager().syncPlayerOverridesFromPdc(player);
        plugin.getTaskScheduler().runTaskLater(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getNametagManager().isHiddenOtherNametags(player)) {
                plugin.getNametagManager().hideAllOthersNametagsFromViewer(player);
            }
        }, 1L);
        plugin.getTaskScheduler().runTaskLater(() -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getNametagManager().reconcileJoinNametagPacketsForOwner(player);
        }, 12L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        onlinePlayers.put(event.getPlayer().getUniqueId(), event.getPlayer());
        playerNameId.put(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            if (event.getPlayer().isOnline()) {
                plugin.getNametagManager().addPlayer(event.getPlayer(), true);
            }
        }, 6);
        plugin.getTaskScheduler().runTaskLater(() -> {
            if (event.getPlayer().isOnline()) {
                plugin.getNametagManager().applyPreferencesFromPersistentData(event.getPlayer());
            }
        }, 22L);
        playerEntityId.put(event.getPlayer().getEntityId(), event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.getPacketEventsListener().removePlayerData(event.getPlayer());
        playerNameId.remove(event.getPlayer().getName());
        diedPlayers.remove(event.getPlayer().getUniqueId());
        cancelTeleportSync(event.getPlayer().getUniqueId());
        zeroDamageRecoveryRunIds.remove(event.getPlayer().getUniqueId());
        cancelRespawnShow(event.getPlayer().getUniqueId());
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            plugin.getNametagManager().removePlayer(event.getPlayer());
            plugin.getNametagManager().clearCache(event.getPlayer().getUniqueId());
            plugin.getPlaceholderManager().removePlayer(event.getPlayer());
            diedPlayers.remove(event.getPlayer().getUniqueId());
            onlinePlayers.remove(event.getPlayer().getUniqueId());
            playerEntityId.remove(event.getPlayer().getEntityId());
            playerWorlds.remove(event.getPlayer().getUniqueId());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(@NotNull EntityPotionEffectEvent event) {
        final Entity entity = ((EntityEvent) event).getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player is in spectator mode, skipping potion event");
            }
            return;
        }

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) {
            if (event.getNewEffect() == null
                    || (!event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY))) {
                if (plugin.getNametagManager().isDebug()) {
                    plugin.getLogger().info("Potion effect is not invisibility, skipping potion event");
                }
                return;
            }
            plugin.getNametagManager().removeAllViewers(player);
            plugin.getNametagManager().blockPlayer(player);
        } else if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED
                || event.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (event.getOldEffect() == null || !event.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY)) {
                if (plugin.getNametagManager().isDebug()) {
                    plugin.getLogger().info("Potion effect is not invisibility, skipping potion event: "
                            + (event.getOldEffect() != null ? event.getOldEffect().getType() : null));
                }
                return;
            }

            plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                plugin.getNametagManager().unblockPlayer(player);
                plugin.getNametagManager().showToTrackedPlayers(player);
            }, 3);

        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().unblockPlayer(e.getPlayer());
            plugin.getNametagManager().showToTrackedPlayers(e.getPlayer());
        } else if (e.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getNametagManager().removeAllViewers(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        if (event.isCancelled()) {
            scheduleCancelledDeathRecovery(event.getEntity());
            return;
        }

        diedPlayers.add(event.getEntity().getUniqueId());
        plugin.getNametagManager().removeAllViewers(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onZeroDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFinalDamage() > 0) {
            return;
        }

        scheduleZeroDamageRecovery(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        diedPlayers.remove(player.getUniqueId());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            cancelRespawnShow(player.getUniqueId());
            return;
        }

        scheduleRespawnShow(player);
    }

    private void scheduleCancelledDeathRecovery(@NotNull Player player) {
        final long[] delays = {5L, 20L, 60L, 100L};
        for (long delay : delays) {
            plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                recoverNametagVisibility(player);
            }, delay);
        }
    }

    private void scheduleZeroDamageRecovery(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        final UUID runId = UUID.randomUUID();
        if (zeroDamageRecoveryRunIds.putIfAbsent(uuid, runId) != null) {
            return;
        }
        for (long delay : TELEPORT_SYNC_DELAYS) {
            plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                if (!runId.equals(zeroDamageRecoveryRunIds.get(uuid))) {
                    return;
                }
                recoverNametagVisibility(player);
                if (delay == TELEPORT_SYNC_DELAYS[TELEPORT_SYNC_DELAYS.length - 1]) {
                    zeroDamageRecoveryRunIds.remove(uuid, runId);
                }
            }, delay);
        }
    }

    /**
     * Schedules the post-respawn show after 5 ticks, cancelling any previous pending show task.
     * <p>
     * Guards against a death/respawn race: if the player dies again before the task runs, {@link #cancelRespawnShow}
     * is called from a new respawn, or the task itself skips the show when the player is once again dead. This prevents
     * the nametag from reappearing during a second death's screen when death+respawn happened in the same tick.
     */
    private void scheduleRespawnShow(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        cancelRespawnShow(uuid);
        final MyScheduledTask task = plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            respawnShowTasks.remove(uuid);
            if (!player.isOnline() || player.isDead() || diedPlayers.contains(uuid)) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            plugin.getNametagManager().showToTrackedPlayers(player);
        }, 5);
        respawnShowTasks.put(uuid, task);
    }

    private void cancelRespawnShow(@NotNull UUID uuid) {
        final MyScheduledTask task = respawnShowTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVanishes(@NotNull PlayerShowEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getConfigManager().getSettings().getVisibility().isShowWhileLooking()) {
            return;
        }

        plugin.getNametagManager().getPacketDisplays(player).forEach(display -> {
            display.showToPlayer(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerUnvanishes(@NotNull PlayerHideEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        plugin.getNametagManager().getPacketDisplays(player).forEach(display -> {
            display.hideFromPlayer(event.getPlayer());
        });
    }

    private void handlePositionChange(@NotNull Player player, @NotNull Location pos1, @NotNull Location pos2) {
        final boolean worldChange = pos1.getWorld() != pos2.getWorld();

        if (worldChange) {
            plugin.getTrackerManager().forceUntrack(player);
        }

        scheduleTeleportSync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        handlePositionChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    public void logicElytra(Player player) {
        if (!plugin.getNametagManager().isEffectiveShowOwnNametag(player)) {
            return;
        }

        plugin.getNametagManager().getPacketDisplays(player).forEach(packetDisplayText -> {
            packetDisplayText.hideForOwner();

            plugin.getTaskScheduler().runTaskLaterAsynchronously(packetDisplayText::showForOwner, 5);
        });
    }
    
    private void scheduleTeleportSync(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        cancelTeleportSync(uuid);
        final UUID runId = UUID.randomUUID();
        teleportSyncRunIds.put(uuid, runId);
        scheduleTeleportSyncAttempt(player, uuid, runId, 0);
    }

    private void scheduleTeleportSyncAttempt(@NotNull Player player, @NotNull UUID uuid, @NotNull UUID runId, int attempt) {
        final long delay = TELEPORT_SYNC_DELAYS[Math.min(attempt, TELEPORT_SYNC_DELAYS.length - 1)];
        final MyScheduledTask task = plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            if (!runId.equals(teleportSyncRunIds.get(uuid))) {
                return;
            }
            if (!player.isOnline()) {
                teleportSyncTasks.remove(uuid);
                teleportSyncRunIds.remove(uuid, runId);
                return;
            }

            recoverNametagVisibility(player);

            if (attempt + 1 < TELEPORT_SYNC_DELAYS.length) {
                scheduleTeleportSyncAttempt(player, uuid, runId, attempt + 1);
            } else {
                teleportSyncTasks.remove(uuid);
                teleportSyncRunIds.remove(uuid, runId);
            }
        }, delay);

        teleportSyncTasks.put(uuid, task);
    }

    private void recoverNametagVisibility(@NotNull Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        plugin.getTrackerManager().reconcileTrackedState(player);
        plugin.getNametagManager().showToTrackedPlayers(player);
        plugin.getNametagManager().updateDisplaysForPlayer(player);
        if (plugin.getNametagManager().isEffectiveShowOwnNametag(player)) {
            plugin.getNametagManager().showToOwner(player);
        }
    }

    private void cancelTeleportSync(@NotNull UUID uuid) {
        final MyScheduledTask task = teleportSyncTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        teleportSyncRunIds.remove(uuid);
    }

    @Nullable
    public Player getPlayer(@NotNull UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public void close() {
        diedPlayers.clear();
        playerEntityId.clear();
        playerNameId.clear();
        onlinePlayers.clear();
        teleportSyncTasks.values().forEach(MyScheduledTask::cancel);
        teleportSyncTasks.clear();
        teleportSyncRunIds.clear();
        zeroDamageRecoveryRunIds.clear();
        respawnShowTasks.values().forEach(MyScheduledTask::cancel);
        respawnShowTasks.clear();
    }

}

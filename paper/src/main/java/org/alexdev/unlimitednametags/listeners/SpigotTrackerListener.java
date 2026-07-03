package org.alexdev.unlimitednametags.listeners;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class SpigotTrackerListener implements Listener {

    private final UnlimitedNameTags plugin;

    public SpigotTrackerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        startCheckTask();
    }

    private void startCheckTask() {
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            final Map<UUID, Set<UUID>> currentlyTrackedByViewer = currentTrackedTargetsByViewer();

            for (Player player : Bukkit.getOnlinePlayers()) {
                final Set<UUID> trackedPlayers = plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId());
                final Set<UUID> current = currentlyTrackedByViewer.getOrDefault(player.getUniqueId(), Set.of());

                final Set<UUID> toRemove = Sets.difference(trackedPlayers, current);
                final Set<UUID> toAdd = Sets.difference(current, trackedPlayers);

                toRemove.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(p -> plugin.getTrackerManager().handleRemove(player, p));

                toAdd.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(p -> plugin.getTrackerManager().handleAdd(player, p));
            }
        }, 0, 5);
    }

    private Map<UUID, Set<UUID>> currentTrackedTargetsByViewer() {
        final Map<UUID, Set<UUID>> trackedByViewer = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            for (Player viewer : target.getTrackedBy()) {
                if (viewer.equals(target)) {
                    continue;
                }
                trackedByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>())
                        .add(target.getUniqueId());
            }
        }
        return trackedByViewer;
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        plugin.getTrackerManager().handleQuit(player);
    }

}

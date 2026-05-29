package org.alexdev.unlimitednametags.listeners;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpigotTrackerListener implements Listener {

    private final UnlimitedNameTags plugin;

    public SpigotTrackerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        startCheckTask();
    }

    private void startCheckTask() {
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                final Set<UUID> trackedPlayers = plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId());
                final Set<UUID> current = player.getTrackedBy().stream()
                        .map(Player::getUniqueId)
                        .collect(Collectors.toSet());

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

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        plugin.getTrackerManager().handleQuit(player);
    }

}

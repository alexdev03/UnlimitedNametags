package org.alexdev.unlimitednametags.listeners;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class PaperTrackerListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler
    public void onTrack(@NotNull PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (!target.isOnline()) {
            return;
        }

        plugin.getTrackerManager().handleAdd(event.getPlayer(), target);
    }

    @EventHandler
    public void onUnTrack(@NotNull PlayerUntrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        plugin.getTrackerManager().handleRemove(event.getPlayer(), target);
    }

}

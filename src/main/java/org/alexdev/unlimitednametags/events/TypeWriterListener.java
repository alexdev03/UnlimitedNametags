package org.alexdev.unlimitednametags.events;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public class TypeWriterListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler
    public void onJoin(AsyncCinematicEndEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().addPlayer(event.getPlayer()),
                5);

        plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer());
    }

}

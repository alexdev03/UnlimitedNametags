package org.alexdev.unlimitednametags.events;

import lombok.RequiredArgsConstructor;
import me.gabber235.typewriter.events.AsyncCinematicEndEvent;
import me.gabber235.typewriter.events.AsyncCinematicStartEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public class TypeWriterListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler
    public void onStart(AsyncCinematicStartEvent event) {
        plugin.getNametagManager().blockPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    plugin.getNametagManager().hideAllDisplays(event.getPlayer());
                    plugin.getNametagManager().removePlayer(event.getPlayer(), true);
                },
                1);
    }

    @EventHandler
    public void onEnd(AsyncCinematicEndEvent event) {
        plugin.getNametagManager().unblockPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().addPlayer(event.getPlayer()),
                1);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer()),
                5);
    }


}

package org.alexdev.unlimitednametags.hook;

import me.gabber235.typewriter.events.AsyncCinematicEndEvent;
import me.gabber235.typewriter.events.AsyncCinematicStartEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class TypeWriterListener extends Hook implements Listener {

    public TypeWriterListener(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @EventHandler
    public void onStart(@NotNull AsyncCinematicStartEvent event) {
        plugin.getNametagManager().blockPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    plugin.getNametagManager().hideAllDisplays(event.getPlayer());
                    plugin.getNametagManager().removePlayer(event.getPlayer());
                },
                1);
    }

    @EventHandler
    public void onEnd(@NotNull AsyncCinematicEndEvent event) {
        plugin.getNametagManager().unblockPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().addPlayer(event.getPlayer()),
                2);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer()),
                5);
    }


    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}

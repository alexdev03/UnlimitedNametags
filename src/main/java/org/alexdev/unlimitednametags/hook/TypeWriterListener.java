package org.alexdev.unlimitednametags.hook;


import com.typewritermc.engine.paper.events.AsyncCinematicEndEvent;
import com.typewritermc.engine.paper.events.AsyncCinematicStartEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public class TypeWriterListener extends Hook implements Listener {

    public TypeWriterListener(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @EventHandler
    public void onStart(@NotNull AsyncCinematicStartEvent event) {
        plugin.getNametagManager().blockPlayer(event.getPlayer());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> plugin.getNametagManager().hideAllDisplays(event.getPlayer()), 1);
    }

    @EventHandler
    public void onEnd(@NotNull AsyncCinematicEndEvent event) {
        plugin.getNametagManager().unblockPlayer(event.getPlayer());
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                    plugin.getNametagManager().unBlockForAllPlayers(event.getPlayer());
                    final Set<Player> viewers = plugin.getTrackerManager().getTrackedPlayers(event.getPlayer().getUniqueId())
                            .stream()
                            .map(Bukkit::getPlayer)
                            .filter(p -> p != null && p != event.getPlayer())
                            .collect(Collectors.toSet());

                    plugin.getNametagManager().getPacketDisplayText(event.getPlayer()).forEach(display -> display.showToPlayers(viewers));
                },
                1);
        plugin.getTaskScheduler().runTaskLaterAsynchronously(
                () -> plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer()), 2);
    }


    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}

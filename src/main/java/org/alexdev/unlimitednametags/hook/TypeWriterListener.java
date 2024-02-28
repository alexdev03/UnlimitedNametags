package org.alexdev.unlimitednametags.hook;

import me.gabber235.typewriter.events.AsyncCinematicEndEvent;
import me.gabber235.typewriter.events.AsyncCinematicStartEvent;
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getNametagManager().hideAllDisplays(event.getPlayer()), 1);
    }

    @EventHandler
    public void onEnd(@NotNull AsyncCinematicEndEvent event) {
        plugin.getNametagManager().unblockPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    final Set<Player> viewers = plugin.getPlayerListener().getTrackedPlayers().get(event.getPlayer().getUniqueId())
                            .stream()
                            .map(Bukkit::getPlayer)
                            .filter(p -> p != null && p != event.getPlayer())
                            .collect(Collectors.toSet());
                    plugin.getNametagManager().getPacketDisplayText(event.getPlayer()).ifPresent(display -> display.showToPlayers(viewers));
                },
                1);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getNametagManager().updateDisplaysForPlayer(event.getPlayer()),
                2);
    }


    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}

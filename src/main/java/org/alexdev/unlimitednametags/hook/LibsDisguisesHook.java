package org.alexdev.unlimitednametags.hook;

import me.libraryaddict.disguise.events.DisguiseEvent;
import me.libraryaddict.disguise.events.UndisguiseEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class LibsDisguisesHook extends Hook implements Listener {

    public LibsDisguisesHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void onDisguise(DisguiseEvent event) {
        if (event.getDisguise().getEntity() instanceof Player player) {
            plugin.getNametagManager().removeAllViewers(player);
            plugin.getNametagManager().blockPlayer(player);
        }
    }

    @EventHandler
    public void onUnDisguise(UndisguiseEvent event) {
        if (event.isBeingReplaced()) {
            return;
        }
        if (event.getDisguise().getEntity() instanceof Player player) {
            plugin.getNametagManager().unblockPlayer(player);
            plugin.getNametagManager().showToTrackedPlayers(player);
        }
    }
}

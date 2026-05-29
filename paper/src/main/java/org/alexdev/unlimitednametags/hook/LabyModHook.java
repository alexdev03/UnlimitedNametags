package org.alexdev.unlimitednametags.hook;

import net.labymod.serverapi.server.bukkit.event.LabyModPlayerJoinEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LabyModHook extends Hook implements Listener {

//    private final List<String> modsToBlock = List.of("Nametag", "nametag");


    public LabyModHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onJoin(LabyModPlayerJoinEvent event) {
//        event.labyModPlayer().disableAddons(modsToBlock);
    }


}

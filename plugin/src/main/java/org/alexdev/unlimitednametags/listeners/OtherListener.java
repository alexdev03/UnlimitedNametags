package org.alexdev.unlimitednametags.listeners;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public class OtherListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler
    private void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("PlaceholderAPI")) {
            plugin.getPlaceholderManager().getPapiManager().checkPapiEnabled();
        }
    }
}

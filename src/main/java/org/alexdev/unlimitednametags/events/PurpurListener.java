package org.alexdev.unlimitednametags.events;

import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public class PurpurListener implements Listener {

    private final UnlimitedNameTags plugin;

    @EventHandler()
    public void onTeleportCancelled(EntityTeleportHinderedEvent e) {
        if (e.getReason() != EntityTeleportHinderedEvent.Reason.IS_VEHICLE) {
            return;
        }

        if(!(e.getEntity() instanceof Player player)) return;

        player.eject();
        e.setShouldRetry(true);
    }


}

package org.alexdev.unlimitednametags.hook;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import net.digitalingot.feather.serverapi.api.FeatherAPI;
import net.digitalingot.feather.serverapi.api.event.player.PlayerHelloEvent;
import net.digitalingot.feather.serverapi.api.model.FeatherMod;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class FeatherClientHook extends Hook {

    private final List<FeatherMod> modsToBlock = Collections.singletonList(new FeatherMod("nametags"));

    public FeatherClientHook(UnlimitedNameTags plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        FeatherAPI.getEventService().subscribe(PlayerHelloEvent.class, event -> {
            final Player player = plugin.getPlayerListener().getPlayer(event.getPlayer().getUniqueId());
            if (player == null) {
                return;
            }
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

            if (user.getClientVersion().isOlderThan(ClientVersion.V_1_19_3)) {
                return;
            }

            event.getPlayer().blockMods(modsToBlock);
        });
    }

    @Override
    public void onDisable() {
    }


}

package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.FakeChannelUtil;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import io.github.retrooper.packetevents.util.FoliaCompatUtil;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@RequiredArgsConstructor
public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(true)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        PacketEvents.getAPI().init();
        inject();
    }

    @SuppressWarnings("deprecation")
    private void inject() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            SpigotChannelInjector injector = (SpigotChannelInjector) PacketEvents.getAPI().getInjector();

            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) {
                //We did not inject this user
                Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
                //Check if it is a fake connection...
                if (!FakeChannelUtil.isFakeChannel(channel)) {
                    //Kick them, if they are not a fake player.
                    FoliaCompatUtil.runTaskForEntity(player, plugin, () -> player.kickPlayer("PacketEvents 2.0 failed to inject"), null, 0);
                }
                return;
            }

            // Set bukkit player object in the injectors
            injector.updatePlayer(user, player);
        });
    }

    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetaData(event);
        } else if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        }
    }

    private void handleMetaData(@NotNull PacketSendEvent event) {
        if(event.getUser().getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_20_2)) {
            return;
        }
        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        final @NotNull Optional<TextDisplay> textDisplay = plugin.getNametagManager().getEntityById(packet.getEntityId());

        if (textDisplay.isEmpty()) {
            return;
        }

        for (final EntityData eData : packet.getEntityMetadata()) {
            if (eData.getIndex() == 10) {
                final Vector3f old = (Vector3f) eData.getValue();
                final Vector3f newV = new Vector3f(old.getX(), old.getY() + 0.45f, old.getZ());
                eData.setValue(newV);
                event.markForReEncode(true);
                return;
            }
        }
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag()) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }
}

package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import io.github.retrooper.packetevents.util.viaversion.ViaVersionUtil;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public void onLoad() {
//        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
//        //Are all listeners read only?
//        PacketEvents.getAPI().getSettings().reEncodeByDefault(true)
//                .checkForUpdates(false)
//                .bStats(true);
//        PacketEvents.getAPI().load();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
//        PacketEvents.getAPI().init();
        inject();
    }

    private void inject() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            final SpigotChannelInjector injector = (SpigotChannelInjector) PacketEvents.getAPI().getInjector();
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

//            injector.updatePlayer(user, player);
        });
    }

    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetaData(event);
        }
    }


    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            handleUseEntity(event);
        }
    }

    private void handleUseEntity(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        final WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
        final Optional<? extends Player> player = Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == packet.getEntityId()).findFirst();
        if (player.isEmpty()) {
            return;
        }
        switch (packet.getAction()) {
            case START_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), true);
            case STOP_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), false);
        }
    }

    private void handlePassengers(PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == packet.getEntityId()).findFirst();
        if (player.isEmpty()) {
            return;
        }

        final Optional<PacketDisplayText> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player.get());
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }

        plugin.getPacketManager().setPassengers(player.get(), Arrays.stream(packet.getPassengers()).boxed().toList());
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if(!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag() &&
                !plugin.getConfigManager().getSettings().isDisableDefaultNameTagBedrock() &&
                plugin.getFloodgateHook()
                        .map(h -> h.isBedrock(player))
                        .orElse(player.getName().startsWith("*"))
        ) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }

    private int getProtocolVersion(@NotNull Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            return ViaVersionUtil.getProtocolVersion(player);
        }
        return PacketEvents.getAPI().getPlayerManager().getUser(player).getClientVersion().getProtocolVersion();
    }

    private void handleMetaData(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        int protocol = getProtocolVersion(player);
        final Optional<ClientVersion> clientVersionOptional = Arrays.stream(ClientVersion.values()).filter(p -> p.getProtocolVersion() == protocol).findFirst();
        if (clientVersionOptional.isEmpty()) {
            return;
        }

        final ClientVersion clientVersion = clientVersionOptional.get();
        //handle metadata for : bedrock players && client with version 1.20.1 or lower
        if (clientVersion.isNewerThanOrEquals(ClientVersion.V_1_20_2) &&
                !plugin.getFloodgateHook().map(h -> h.isBedrock(player)).orElse(player.getName().startsWith("*"))) {
            return;
        }

        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        final @NotNull Optional<PacketDisplayText> textDisplay = plugin.getNametagManager().getPacketDisplayText(packet.getEntityId());

        if (textDisplay.isEmpty()) {
            return;
        }

        for (final EntityData eData : packet.getEntityMetadata()) {
            if (eData.getIndex() == 11) {

                final Vector3f old = (Vector3f) eData.getValue();
                final Vector3f newV = new Vector3f(old.getX(), old.getY() + 0.45f, old.getZ());
                eData.setValue(newV);
                event.markForReEncode(true);
                return;
            }
        }
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
//        PacketEvents.getAPI().terminate();
    }
}

package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public PacketEventsListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
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
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            return;
        }
        switch (packet.getAction()) {
            case START_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), true);
            case STOP_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), false);
            case START_FLYING_WITH_ELYTRA -> plugin.getPlayerListener().logicElytra(player.get());
        }
    }

    private void handlePassengers(@NotNull PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            return;
        }

        final Optional<PacketNameTag> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player.get());
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }

        final List<Integer> passengers = Arrays.stream(packet.getPassengers()).boxed().collect(Collectors.toList());

        plugin.getPacketManager().setPassengers(player.get(), passengers);
        if(!passengers.contains(optionalPacketDisplayText.get().getEntityId())) {
            passengers.add(optionalPacketDisplayText.get().getEntityId());
            packet.setPassengers(passengers.stream().mapToInt(i -> i).toArray());
        }
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag()) {
            return;
        }

        final Player player = Bukkit.getPlayer(event.getUser().getUUID());
        if(player == null) {
            return;
        }

        if(plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(player)).orElse(false)) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }

    private void handleMetaData(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        int protocol = event.getUser().getClientVersion().getProtocolVersion();
        //handle metadata for : bedrock players && client with version 1.20.1 or lower
        if (protocol >= 764) {
            return;
        }

        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        final Optional<PacketNameTag> textDisplay = plugin.getNametagManager().getPacketDisplayText(packet.getEntityId());
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
    }
}

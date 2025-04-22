package org.alexdev.unlimitednametags.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.google.common.collect.Maps;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.data.TeamData;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, Map<String, TeamData>> teams;

    public PacketEventsListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.teams = Maps.newConcurrentMap();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @NotNull
    public Map<String, TeamData> getTeams(@NotNull UUID player) {
        return teams.computeIfAbsent(player, p -> Maps.newConcurrentMap());
    }

    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetaData(event);
        } else if (event.getPacketType() == PacketType.Play.Server.CAMERA) {
            handleCamera(event);
        }
    }

    private void handleCamera(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }

        final WrapperPlayServerCamera camera = new WrapperPlayServerCamera(event);
        if (camera.getCameraId() == player.getEntityId()) {
            plugin.getNametagManager().getPacketDisplayText(player).ifPresent(PacketNameTag::showForOwner);
        } else {
            plugin.getNametagManager().getPacketDisplayText(player).ifPresent(PacketNameTag::hideForOwner);
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

        if (plugin.getConfigManager().getSettings().isForceDisableDefaultNameTag()) {
            if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
                packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
                event.markForReEncode(true);
            }

            return;
        }

        final Map<String, TeamData> teams = getTeams(player.getUniqueId());
        final String teamName = packet.getTeamName();

        switch (packet.getTeamMode()) {
            case ADD_ENTITIES -> {
                final Optional<TeamData> teamData = Optional.ofNullable(teams.get(teamName));
                if (teamData.isEmpty()) {
                    return;
                }

                teamData.get().getMembers().addAll(packet.getPlayers());
                // If the team was visible and now contains online players, update the visibility
                if (!teamData.get().isChangedVisibility() && packet.getPlayers().stream().anyMatch(p -> Bukkit.getPlayer(p) != null)) {
                    teamData.get().setChangedVisibility(true);
                    final WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = teamData.get().getTeamInfo();
                    teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.getUser().sendPacket(new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE, teamData.get().getTeamInfo(), teamData.get().getMembers()));
                }
            }
            case REMOVE_ENTITIES -> {
                final Optional<TeamData> teamData = Optional.ofNullable(teams.get(teamName));
                if (teamData.isEmpty()) {
                    return;
                }

                teamData.get().getMembers().removeAll(packet.getPlayers());
            }
            case CREATE -> {
                if (teams.containsKey(teamName)) {
                    return;
                }

                final TeamData teamData = new TeamData(teamName, packet.getTeamInfo().orElseThrow(), Set.copyOf(packet.getPlayers()));
                teams.put(teamName, teamData);

                if (teamData.getMembers().stream().anyMatch(p -> Bukkit.getPlayer(p) != null)) {
                    teamData.setChangedVisibility(true);
                    teamData.getTeamInfo().setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }
            }
            case UPDATE -> {
                final Optional<TeamData> teamData = Optional.ofNullable(teams.get(teamName));
                if (teamData.isEmpty()) {
                    return;
                }

                final WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = packet.getTeamInfo().orElseThrow();

                if (teamData.get().isChangedVisibility() && teamInfo.getTagVisibility() != WrapperPlayServerTeams.NameTagVisibility.NEVER) {
                    teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }

                teamData.get().setTeamInfo(teamInfo);
            }
            case REMOVE -> teams.remove(teamName);
        }


    }

    public void removePlayerData(@NotNull Player player) {
        teams.remove(player.getUniqueId());
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

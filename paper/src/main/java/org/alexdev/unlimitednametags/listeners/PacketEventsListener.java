package org.alexdev.unlimitednametags.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.google.common.collect.Maps;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.data.TeamData;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PaperNametagRow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, Map<String, TeamData>> teams;
    private final PacketListenerAbstract passengerObserver;

    public PacketEventsListener(UnlimitedNameTags plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.teams = Maps.newConcurrentMap();
        this.passengerObserver = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.isCancelled() || plugin.getPacketManager() == null
                        || event.getPacketType() != PacketType.Play.Server.SET_PASSENGERS) return;
                final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
                // Cache accepted writes before the next packet, even if socket completion is delayed.
                plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId()).ifPresent(owner ->
                        plugin.getPacketManager().observePassengers(event.getUser(), owner, packet.getPassengers()));
            }
        };
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(passengerObserver);
    }

    @NotNull
    public Map<String, TeamData> getTeams(@NotNull UUID player) {
        return teams.computeIfAbsent(player, p -> Maps.newConcurrentMap());
    }

    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.isCancelled() || plugin.getPacketManager() == null || plugin.getNametagManager() == null) return;
        observeEntities(event);
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

        if (!plugin.getNametagManager().isEffectiveShowOwnNametag(player)) {
            return;
        }

        final WrapperPlayServerCamera camera = new WrapperPlayServerCamera(event);
        if (camera.getCameraId() == player.getEntityId()) {
            plugin.getNametagManager().getPacketDisplays(player).forEach(PaperNametagRow::showForOwner);
        } else {
            plugin.getNametagManager().getPacketDisplays(player).forEach(PaperNametagRow::hideForOwner);
        }
    }

    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            handleUseEntity(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
            handlePlayerInput(event);
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

    private void handlePlayerInput(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);
        final Collection<PaperNametagRow> packetNameTags = plugin.getNametagManager().getPacketDisplays(player);

        boolean updateSneaking = packetNameTags.stream()
                .anyMatch(packetNameTag -> packet.isShift() != packetNameTag.isSneaking());

        if (updateSneaking) {
            plugin.getNametagManager().updateSneaking(player, packet.isShift());
        }
    }

    private void handlePassengers(@NotNull PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> owner = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (owner.isEmpty()) return;
        final int[] original = packet.getPassengers();
        if (!plugin.getPacketManager().knowsOwner(event.getUser(), owner.get())
                && Arrays.stream(original).anyMatch(plugin.getPacketManager()::isRow)) {
            event.setCancelled(true);
            return;
        }
        final int[] passengers = plugin.getPacketManager().passengers(event.getUser(), owner.get(), original);
        if (!Arrays.equals(original, passengers)) {
            packet.setPassengers(passengers);
            event.markForReEncode(true);
        }
    }

    private void observeEntities(@NotNull PacketSendEvent event) {
        final User user = event.getUser();
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY
                || event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            final int id = event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY
                    ? new WrapperPlayServerSpawnEntity(event).getEntityId()
                    : new WrapperPlayServerSpawnPlayer(event).getEntityId();
            if (plugin.getPacketManager().isRow(id)) {
                final Optional<PaperNametagRow> row = plugin.getNametagManager().getPacketDisplayByEntityId(id);
                if (row.isEmpty() || row.get().getOwner() == null
                        || !plugin.getPacketManager().knowsOwner(user, row.get().getOwner())
                        || !((PacketNameTag) row.get()).canViewerSee(user.getUUID())) {
                    event.setCancelled(true);
                    return;
                }
            }
            final Runnable spawned = plugin.getPacketManager().trackSpawn(user, id);
            event.getTasksAfterSend().add(() -> {
                if (!event.isCancelled()) spawned.run();
            });
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            // Invalidate at encoding time: later writes must not mount an owner awaiting socket flush.
            for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                plugin.getPacketManager().destroyed(user, id);
                if (plugin.getPacketManager().isCurrent(user)) {
                    plugin.getPlayerListener().getPlayerFromEntityId(id).ifPresent(owner ->
                            plugin.getNametagManager().getPacketDisplays(owner).forEach(row ->
                                    ((PacketNameTag) row).hideFromViewer(user.getUUID())));
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            plugin.getPacketManager().reset(user);
            if (plugin.getPacketManager().isCurrent(user)) {
                plugin.getNametagManager().removeViewer(user.getUUID());
            }
        }
    }


    private boolean preTeamsChecks(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().getBehavior().isDisableDefaultNameTag()) {
            return false;
        }

        if (!(event.getPlayer() instanceof Player player)
                || !plugin.getConfigManager().getSettings().getWorlds().isEnabled(player.getWorld().getName())) {
            return false;
        }

        return event.getUser().getClientVersion().isNewerThan(ClientVersion.V_1_19_3);
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!preTeamsChecks(event)) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (handleForceDisableDefaultNameTag(event, packet)) {
            return;
        }

        final Map<String, TeamData> teams = getTeams(event.getUser().getUUID());
        final String teamName = packet.getTeamName();

        switch (packet.getTeamMode()) {
            case ADD_ENTITIES -> handleAddEntities(event, packet, teams, teamName);
            case REMOVE_ENTITIES -> handleRemoveEntities(packet, teams, teamName);
            case CREATE -> handleCreateTeam(event, packet, teams, teamName);
            case UPDATE -> handleUpdateTeam(event, packet, teams, teamName);
            case REMOVE -> teams.remove(teamName);
        }
    }

    private boolean handleForceDisableDefaultNameTag(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet) {
        if (plugin.getConfigManager().getSettings().getBehavior().isForceDisableDefaultNameTag()) {
            if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
                packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
                event.markForReEncode(true);
            }
            return true;
        }
        return false;
    }

    private void handleAddEntities(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet,
                                   @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        if (teamDataOpt.isEmpty()) {
            return;
        }

        final TeamData teamData = teamDataOpt.get();
        teamData.getMembers().addAll(packet.getPlayers());

        if (!teamData.isChangedVisibility() && packet.getPlayers().stream().anyMatch(this::existsPlayer)) {
            teamData.setChangedVisibility(true);
            final WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = teamData.getTeamInfo();
            teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
            if (teamData.getTeamInfo() != null) {
                event.getUser().sendPacket(new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE, teamData.getTeamInfo(), teamData.getMembers()));
            }
        }
    }

    private void handleRemoveEntities(@NotNull WrapperPlayServerTeams packet, @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        teamDataOpt.ifPresent(teamData -> teamData.getMembers().removeAll(packet.getPlayers()));
    }

    private void handleCreateTeam(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet,
                                  @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        if (teams.containsKey(teamName)) {
            return;
        }

        packet.getTeamInfo().ifPresent(teamInfo -> {
            final TeamData teamData = new TeamData(teamName, teamInfo, Set.copyOf(packet.getPlayers()));
            teams.put(teamName, teamData);

            if (teamData.getMembers().stream().anyMatch(this::existsPlayer)) {
                teamData.setChangedVisibility(true);
                // Ensure teamInfo is not null before setting visibility
                if (teamData.getTeamInfo() != null) {
                    teamData.getTeamInfo().setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    event.markForReEncode(true);
                }
            }
        });
    }

    private void handleUpdateTeam(@NotNull PacketSendEvent event, @NotNull WrapperPlayServerTeams packet, @NotNull Map<String, TeamData> teams, @NotNull String teamName) {
        final Optional<TeamData> teamDataOpt = Optional.ofNullable(teams.get(teamName));
        if (teamDataOpt.isEmpty()) {
            return;
        }

        final TeamData teamData = teamDataOpt.get();
        packet.getTeamInfo().ifPresent(teamInfoFromPacket -> {
            if (teamData.isChangedVisibility() && teamInfoFromPacket.getTagVisibility() != WrapperPlayServerTeams.NameTagVisibility.NEVER) {
                teamInfoFromPacket.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                event.markForReEncode(true);
            }
            teamData.setTeamInfo(teamInfoFromPacket);
        });
    }

    public void removePlayerData(@NotNull Player player) {
        teams.remove(player.getUniqueId());
    }

    public void onWorldChange(@NotNull Player player) {
        final Map<String, TeamData> viewerTeams = getTeams(player.getUniqueId());
        final boolean enabled = plugin.getConfigManager().getSettings().getWorlds()
                .isEnabled(player.getWorld().getName());
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        viewerTeams.forEach((teamName, teamData) -> {
            final boolean shouldHide = enabled && teamData.getMembers().stream().anyMatch(this::existsPlayer);
            if (shouldHide == teamData.isChangedVisibility() || teamData.getTeamInfo() == null) {
                return;
            }
            teamData.getTeamInfo().setTagVisibility(shouldHide
                    ? WrapperPlayServerTeams.NameTagVisibility.NEVER
                    : teamData.getOriginalVisibility());
            teamData.setChangedVisibility(shouldHide);
            user.sendPacket(new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.UPDATE,
                    teamData.getTeamInfo(), teamData.getMembers()));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
        final Optional<PaperNametagRow> textDisplay = plugin.getNametagManager().getPacketDisplayByEntityId(packet.getEntityId());
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

    public boolean existsPlayer(@NotNull String name) {
        return plugin.getPlayerListener().getPlayerNameId().containsKey(name);
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (plugin.getPacketManager() != null) plugin.getPacketManager().reset(event.getUser());
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(passengerObserver);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}

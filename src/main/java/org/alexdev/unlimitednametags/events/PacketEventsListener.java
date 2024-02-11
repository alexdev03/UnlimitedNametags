package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.google.common.collect.ImmutableList;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().reEncodeByDefault(true)
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        PacketEvents.getAPI().init();
        inject();
    }

    private void inject() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            final SpigotChannelInjector injector = (SpigotChannelInjector) PacketEvents.getAPI().getInjector();

            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

            injector.updatePlayer(user, player);
        });
    }

    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        }
        if (true) return;
        if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnEntity(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            handlePlayerSpawn(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            handlePlayerSpawn(event);
        }
//        if(event.getPacketType().getName().contains("CHUNK")) {
//            return;
//        }
//        if(event.getPacketType() == PacketType.Play.Server.PLUGIN_MESSAGE || event.getPacketType() == PacketType.Play.Server.TIME_UPDATE || event.getPacketType() == PacketType.Play.Server.NBT_QUERY_RESPONSE) {
//            return;
//        }
//        if(event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE || event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
//            return;
//        }

    }

    private void handleDestroyEntities(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final WrapperPlayServerDestroyEntities destroyEntities = new WrapperPlayServerDestroyEntities(event);
        final int[] entityIds = destroyEntities.getEntityIds();
        final List<Player> players = ImmutableList.copyOf(Bukkit.getOnlinePlayers());
        Arrays.stream(Arrays.copyOf(entityIds, entityIds.length))
                .mapToObj(id -> players.stream().filter(p -> p.getEntityId() == id).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(target -> plugin.getNametagManager().getPacketDisplayText(target).ifPresent(packetDisplayText -> {
                    plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                        if (packetDisplayText.canPlayerSee(player)) {
                            packetDisplayText.hideFromPlayer(player);
                        }
                    }, 2);
                }));
    }

    private void handlePlayerSpawn(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            final Optional<? extends Player> optionalPlayer = Optional.ofNullable(Bukkit.getPlayer(packet.getUUID()));
            if (optionalPlayer.isEmpty()) {
                return;
            }
            final Player target = optionalPlayer.get();
            handlePlayerSpawn(player, target);
        }, 4);
    }

    private void handleSpawnEntity(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            plugin.getLogger().warning("Failed to get player from event: " + event.getPlayer());
            return;
        }
        final WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
        final int entityId = packet.getEntityId();
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            final Optional<? extends Player> optionalPlayer = Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == entityId).findFirst();
            if (optionalPlayer.isEmpty()) {
                return;
            }
            final Player target = optionalPlayer.get();
            handlePlayerSpawn(player, target);
        }, 4);
    }

    private void handlePlayerSpawn(@NotNull Player player, @NotNull Player target) {
        final Optional<PacketDisplayText> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player);
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }
        final PacketDisplayText packetDisplayText = optionalPacketDisplayText.get();
        if (!packetDisplayText.canPlayerSee(target)) {
            packetDisplayText.showToPlayer(target);
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
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag() &&
                !plugin.getConfigManager().getSettings().isDisableDefaultNameTagBedrock() && plugin.getFloodgateHook().map(h -> h.isBedrock((Player) event.getPlayer()))
                .orElse(((Player) event.getPlayer()).getName().startsWith("*"))) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        PacketEvents.getAPI().terminate();
    }
}

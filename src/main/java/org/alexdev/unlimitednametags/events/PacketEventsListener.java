package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.retrooper.packetevents.injector.SpigotChannelInjector;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PacketEventsListener extends PacketListenerAbstract {

    private final UnlimitedNameTags plugin;

    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(false)
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

            // Set bukkit player object in the injectors
            injector.updatePlayer(user, player);
        });
    }

    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnPlayer(event);
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(event);
        }
    }

    private void handleDestroyEntities(PacketSendEvent event) {
        if(!(event.getPlayer() instanceof Player player)) {
            return;
        }
        WrapperPlayServerDestroyEntities destroyEntities = new WrapperPlayServerDestroyEntities(event);
        Arrays.stream(destroyEntities.getEntityIds())
                .mapToObj(id -> Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == id).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(target -> {
//                    System.out.println("Found player " + target.getName() + " for " + player.getName());
                    plugin.getNametagManager().getPacketDisplayText(target).ifPresent(packetDisplayText -> {
                        if (packetDisplayText.canPlayerSee(player)) {
                            packetDisplayText.hideFromPlayer(player);
                        }
                    });
                });
    }

    private void handleSpawnPlayer(PacketSendEvent event) {
        final WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity(event.clone());
        if (spawnEntity.getEntityType() != EntityTypes.PLAYER) {
            return;
        }
        final WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
        final Optional<Player> targetOptional = Optional.ofNullable(Bukkit.getPlayer(packet.getUUID()));
        if (targetOptional.isEmpty()) {
            return;
        }
        final Player target = targetOptional.get();
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (plugin.getPlayerListener().isJustJoined(player.getUniqueId())) {
            return;
        }

        if(plugin.getPlayerListener().isJustTeleported(player.getUniqueId())) {
            return;
        }
        //USELESS NOW
//        if(true) return;

//        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
//            plugin.getNametagManager().getPacketDisplayText(target).ifPresent(packetDisplayText -> {
//                packetDisplayText.hideFromPlayerSilenty(player);
//                if (!packetDisplayText.canPlayerSee(player)) {
//                    packetDisplayText.showToPlayer(player);
//                }
//            });
//        }, 3);
    }

    private void handlePassengers(PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = Bukkit.getOnlinePlayers().stream().filter(p -> p.getEntityId() == packet.getEntityId()).findFirst();
        if (player.isEmpty()) {
            return;
        }

        System.out.println(Arrays.toString(packet.getPassengers()) + event.getUser().getName());

        final Optional<PacketDisplayText> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player.get());
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }

        plugin.getPacketManager().setPassengers(player.get(), Arrays.stream(packet.getPassengers()).boxed().toList());
        final Set<Integer> passengers = Arrays.stream(packet.getPassengers()).boxed().collect(Collectors.toSet());
        if (passengers.contains(packet.getEntityId()) || true) {
            return;
        }
        passengers.add(packet.getEntityId());
        plugin.getPacketManager().setPassengers(player.get(), passengers);
        packet.setPassengers(passengers.stream().mapToInt(i -> i).toArray());
        event.markForReEncode(true);
        System.out.println("Handled packet");
//        System.out.println("Received passengers packet from " + player.get().getName() + " with " + Arrays.toString(packet.getPassengers()) + " passengers of " + packet.getEntityId() + "(" + player.get().getName() + ")");
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

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        PacketEvents.getAPI().terminate();
    }
}

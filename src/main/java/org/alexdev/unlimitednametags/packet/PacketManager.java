package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import me.tofaa.entitylib.EntityLib;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager {

    private final UnlimitedNameTags plugin;
    private final Multimap<UUID, Integer> passengers;

    public PacketManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.initialize();
        this.rangeTask();
        this.passengers = Multimaps.newSetMultimap(new ConcurrentHashMap<>(), ConcurrentHashMap::newKeySet);
    }

    private void initialize() {
        EntityLib.init(PacketEvents.getAPI());
        EntityLib.enableEntityInteractions();
    }

    private void rangeTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            final float range = plugin.getConfigManager().getSettings().getViewDistance() * 160;
            for (var player : plugin.getServer().getOnlinePlayers()) {
                final Optional<PacketDisplayText> packetDisplayTextOptional = plugin.getNametagManager().getPacketDisplayText(player);
                if (packetDisplayTextOptional.isEmpty()) {
                    continue;
                }
                final PacketDisplayText packetDisplayText = packetDisplayTextOptional.get();
                for (var target : plugin.getServer().getOnlinePlayers()) {
                    if (player == target) continue;
                    if (player.getWorld() != target.getWorld()) continue;
                    double distance = player.getLocation().distance(target.getLocation());
                    if (distance <= range - 5 && !packetDisplayText.canPlayerSee(target) && false) {
                        packetDisplayText.showToPlayer(target);
                    } else if (distance > range && packetDisplayText.canPlayerSee(target)) {
                        packetDisplayText.hideFromPlayerSilenty(target);
                    }
                }
            }
        }, 10, 1);
    }

    public void setPassengers(@NotNull Player player, Collection<Integer> passengers) {
        this.passengers.replaceValues(player.getUniqueId(), passengers);
    }

    public void sendPassengersPacket(@NotNull Player player, @NotNull PacketDisplayText packetDisplayText) {
        final int entityId = packetDisplayText.getEntity().getEntityId();
        final int ownerId = packetDisplayText.getOwner().getEntityId();
        final Set<Integer> passengers = new HashSet<>(this.passengers.get(packetDisplayText.getOwner().getUniqueId()));
        passengers.add(entityId);
        final int[] passengersArray = passengers.stream().mapToInt(i -> i).toArray();
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(ownerId, passengersArray);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    public void removePassenger(@NotNull Player player, int passenger) {
        this.passengers.remove(player.getUniqueId(), passenger);
    }

    public void removePassenger(int passenger) {
        this.passengers.values().remove(passenger);
    }

}

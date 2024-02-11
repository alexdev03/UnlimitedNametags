package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import me.tofaa.entitylib.EntityLib;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PacketManager {

    private final UnlimitedNameTags plugin;
    private final Multimap<UUID, Integer> passengers;

    public PacketManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.initialize();
        this.rangeTask();
        this.passengers = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
    }

    private void initialize() {
        EntityLib.init(PacketEvents.getAPI());
        EntityLib.enableEntityInteractions();
    }

    private void rangeTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                for (Player otherPlayer : plugin.getServer().getOnlinePlayers()) {
                    if (player.equals(otherPlayer)) {
                        continue;
                    }
                    final Optional<PacketDisplayText> packetDisplayText = plugin.getNametagManager().getPacketDisplayText(otherPlayer);
                    if (packetDisplayText.isEmpty()) {
                        continue;
                    }
                    if(packetDisplayText.get().canPlayerSee(player)) {
                        packetDisplayText.get().sendPassengersPacket(player);
                    }
                }
            }
        }, 10, 20);
    }

    public void setPassengers(@NotNull Player player, Collection<Integer> passengers) {
        this.passengers.replaceValues(player.getUniqueId(), passengers);
    }

    public void sendPassengersPacket(@NotNull Player player, @NotNull PacketDisplayText packetDisplayText) {
        final int entityId = packetDisplayText.getEntity().getEntityId();
        final int ownerId = packetDisplayText.getOwner().getEntityId();
        final Set<Integer> passengers = Sets.newConcurrentHashSet(this.passengers.get(packetDisplayText.getOwner().getUniqueId()));
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

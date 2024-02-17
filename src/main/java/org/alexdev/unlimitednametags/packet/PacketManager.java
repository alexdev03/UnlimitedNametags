package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PacketManager {

    private final UnlimitedNameTags plugin;
    private final Multimap<UUID, Integer> passengers;
    private final ExecutorService executorService;

    public PacketManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.initialize();
        this.passengers = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
        this.executorService = Executors.newCachedThreadPool();
    }

    private void initialize() {
        SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(plugin);
        APIConfig settings = new APIConfig(PacketEvents.getAPI())
                .debugMode()
                .tickTickables()
                .usePlatformLogger();

        EntityLib.init(platform, settings);
    }

    public void close() {
        this.executorService.shutdown();
    }

    public void setPassengers(@NotNull Player player, @NotNull Collection<Integer> passengers) {
        executorService.submit(() -> this.passengers.replaceValues(player.getUniqueId(), passengers));
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

package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
        this.passengers = (Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet)); //Multimaps.synchronizedMultimap
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            final Thread thread = new Thread(r);
            thread.setName("UnlimitedNameTags-PacketManager");
            return thread;
        });
    }

    private void initialize() {
        final SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(plugin);
        final APIConfig settings = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
        EntityLib.init(platform, settings);
    }

    public void close() {
        this.executorService.shutdown();
    }

    public void setPassengers(@NotNull Player player, @NotNull List<Integer> passengers) {
        final List<Integer> clone = List.copyOf(passengers);
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            executorService.submit(() -> this.passengers.replaceValues(player.getUniqueId(), clone));
        });
    }

    public void sendPassengersPacket(@NotNull Player player, @NotNull PacketNameTag packetNameTag) {
        final int entityId = packetNameTag.getEntityId();
        final int ownerId = packetNameTag.getOwner().getEntityId();
        executorService.submit(() -> {
            final Set<Integer> passengers = Sets.newHashSet(this.passengers.get(packetNameTag.getOwner().getUniqueId()));
            passengers.add(entityId);
            final int[] passengersArray = passengers.stream().mapToInt(i -> i).toArray();
            final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(ownerId, passengersArray);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        });
    }

    public void removePassenger(@NotNull Player player, int passenger) {
        executorService.submit(() -> this.passengers.remove(player.getUniqueId(), passenger));
    }

    public synchronized int getEntityIndex() {
        return SpigotReflectionUtil.generateEntityId();
    }

    public void removePassenger(int passenger) {
        this.passengers.values().remove(passenger);
    }

}

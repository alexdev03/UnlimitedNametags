package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.UntSpigotEntityLibPlatform;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.platform.NametagPassengerSource;
import org.alexdev.unlimitednametags.data.ConcurrentMultimap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class PacketManager {

    private final UnlimitedNameTags plugin;
    private final ConcurrentMultimap<UUID, Integer> passengers;
    private final ExecutorService executorService;

    public PacketManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.initialize();
        this.passengers = new ConcurrentMultimap<>(CopyOnWriteArraySet::new);
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("UnlimitedNameTags-PacketManager-%d")
                .build();
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                namedThreadFactory
        );
    }

    private void initialize() {
        final UntSpigotEntityLibPlatform platform = new UntSpigotEntityLibPlatform(plugin);
        final APIConfig settings = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
        EntityLib.init(platform, settings);
    }

    public void close() {
        this.executorService.shutdown();
    }

    public void setPassengers(@NotNull Player player, @NotNull List<Integer> passengers) {
        this.passengers.replaceValues(player.getUniqueId(), passengers);
    }

    public void sendPassengersPacket(@NotNull User player, Player owner, @NotNull Collection<? extends NametagPassengerSource> packetNameTags) {
        if (packetNameTags.isEmpty()) {
            return;
        }
        final List<Integer> entityIds = packetNameTags.stream()
                .map(NametagPassengerSource::displayEntityId)
                .toList();
        final Set<Integer> entityIdSet = new HashSet<>(entityIds);
        executorService.submit(() -> {
            if (player.getChannel() == null) {
                return;
            }

            final Collection<Integer> ownerPassengers = this.passengers.get(owner.getUniqueId());
            final LinkedHashSet<Integer> passengers = new LinkedHashSet<>(
                    ownerPassengers.size() + entityIds.size());
            ownerPassengers.stream()
                    .filter(passenger -> !entityIdSet.contains(passenger))
                    .forEach(passengers::add);
            passengers.addAll(entityIds);
            final int[] passengersArray = passengers.stream().mapToInt(Integer::intValue).toArray();
            final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(owner.getEntityId(), passengersArray);
            player.sendPacketSilently(packet);
        });
    }

    public void removePassenger(@NotNull Player player, int passenger) {
        this.passengers.remove(player.getUniqueId(), passenger);
    }

    public int getEntityIndex() {
        return Bukkit.getUnsafe().nextEntityId(Bukkit.getWorlds().get(0));
    }

    public void removePassenger(int passenger) {
        this.passengers.removeValueFromAll(passenger);
    }

}

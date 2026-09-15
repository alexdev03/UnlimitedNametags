package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.UntSpigotEntityLibPlatform;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.platform.NametagPassengerSource;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager {
    private final UnlimitedNameTags plugin;
    private final Map<User, PassengerState> connections = new ConcurrentHashMap<>();
    // Retain retired IDs until shutdown so late third-party mounts cannot revive removed rows.
    private final Set<Integer> rowIds = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public PacketManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        EntityLib.init(new UntSpigotEntityLibPlatform(plugin), new APIConfig(PacketEvents.getAPI()).usePlatformLogger());
    }

    public void close() {
        closed = true;
        connections.clear();
        rowIds.clear();
    }

    public boolean isCurrent(User user) {
        if (closed || user == null || user.getChannel() == null) return false;
        Player viewer = plugin.getPlayerListener().getPlayer(user.getUUID());
        return viewer != null && PacketEvents.getAPI().getPlayerManager().getUser(viewer) == user;
    }

    public boolean knowsOwner(User user, Player owner) {
        if (!isCurrent(user) || !owner.isOnline()) return false;
        if (plugin.getPlayerListener().getPlayer(owner.getUniqueId()) != owner) return false;
        if (owner.getUniqueId().equals(user.getUUID())) return true;
        if (plugin.getTrackerManager().isTrackingVetoed(user.getUUID(), owner.getUniqueId())) return false;
        PassengerState state = connections.get(user);
        return state != null && state.knows(owner.getEntityId());
    }

    public Runnable trackSpawn(User user, int entityId) {
        if (!isCurrent(user)) return () -> {};
        final PassengerState state = connections.computeIfAbsent(user, ignored -> new PassengerState());
        final long token = state.beginSpawn(entityId);
        return () -> {
            if (!isCurrent(user) || connections.get(user) != state || !state.completeSpawn(entityId, token)) return;
            plugin.getNametagManager().getPacketDisplayByEntityId(entityId).ifPresent(row ->
                    ((PacketNameTag) row).sendPassengersPacket(user));
            plugin.getPlayerListener().getPlayerFromEntityId(entityId).ifPresent(owner ->
                    plugin.getTaskScheduler().runTask(() -> {
                        final Player viewer = plugin.getPlayerListener().getPlayer(user.getUUID());
                        if (viewer != null && knowsOwner(user, owner)) {
                            plugin.getNametagManager().updateDisplay(viewer, owner);
                        }
                    }));
        };
    }

    public void destroyed(User user, int entityId) {
        PassengerState state = connections.get(user);
        if (state != null) state.destroy(entityId);
    }

    public void reset(User user) {
        // Replace the state to invalidate queued work from the previous world/session.
        connections.remove(user);
    }

    public boolean isRow(int entityId) { return rowIds.contains(entityId); }

    public void setPassengers(@NotNull Player owner, @NotNull List<Integer> passengers) {
        // Compatibility API for explicit owner-wide updates; intercepted packets use the viewer overload.
        for (PassengerState state : connections.values()) {
            if (state.knows(owner.getEntityId())) {
                state.setPassengers(owner.getEntityId(), passengers.stream().mapToInt(Integer::intValue).toArray());
            }
        }
    }

    public int[] passengers(User user, Player owner, int[] original) {
        PassengerState state = connections.computeIfAbsent(user, ignored -> new PassengerState());
        int[] vanilla = Arrays.stream(original).filter(id -> !rowIds.contains(id)).toArray();
        return compose(user, owner, state, vanilla);
    }

    public void observePassengers(User user, Player owner, int[] passengers) {
        if (!isCurrent(user)) return;
        connections.computeIfAbsent(user, ignored -> new PassengerState()).setPassengers(owner.getEntityId(),
                Arrays.stream(passengers).filter(id -> !rowIds.contains(id)).toArray());
    }

    private int[] compose(User user, Player owner, PassengerState state, int[] vanilla) {
        List<Integer> visible = knowsOwner(user, owner)
                ? plugin.getNametagManager().getPacketDisplays(owner).stream()
                    .map(row -> (PacketNameTag) row)
                    .filter(row -> row.canViewerSee(user.getUUID()))
                    .map(PacketNameTag::displayEntityId).toList()
                : List.of();
        return state.compose(vanilla, rowIds, visible);
    }

    public void sendPassengersPacket(@NotNull User viewer, Player owner,
            @NotNull Collection<? extends NametagPassengerSource> ignoredSnapshot) {
        if (!isCurrent(viewer)) return;
        final PassengerState state = connections.computeIfAbsent(viewer, ignored -> new PassengerState());
        ChannelHelper.runInEventLoop(viewer.getChannel(), () -> {
            if (!isCurrent(viewer) || connections.get(viewer) != state || !knowsOwner(viewer, owner)) return;
            // Read the current rows here, after preceding spawn/destroy writes on this connection.
            int[] passengers = compose(viewer, owner, state, state.passengers(owner.getEntityId()));
            // Destroy packets already detach hidden rows; do not emit empty recovery mounts.
            if (Arrays.stream(passengers).noneMatch(rowIds::contains)) return;
            viewer.sendPacket(new WrapperPlayServerSetPassengers(owner.getEntityId(), passengers));
        });
    }

    public void removePassenger(@NotNull Player player, int passenger) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user != null) destroyed(user, passenger);
    }

    public void removePassenger(@NotNull UUID viewerId, int passenger) {
        connections.forEach((user, state) -> {
            if (viewerId.equals(user.getUUID())) state.destroy(passenger);
        });
    }

    public int getEntityIndex() {
        int id = SpigotReflectionUtil.generateEntityId();
        rowIds.add(id);
        return id;
    }

    public void removePassenger(int passenger) {
        connections.values().forEach(state -> state.destroy(passenger));
    }
}

package org.alexdev.unlimitednametags.platform;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.protocol.player.User;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.api.NametagCustomGlowContext;
import org.alexdev.unlimitednametags.api.NametagCustomGlowHandler;
import org.alexdev.unlimitednametags.packet.CustomDisplayAnimationHandler;
import org.alexdev.unlimitednametags.packet.CustomGlowHandler;
import org.alexdev.unlimitednametags.packet.GlowApplyContext;
import org.alexdev.unlimitednametags.packet.PaperNametagRow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BukkitNametagRuntime implements NametagRuntime {

    private static final long PASSENGER_PACKET_DELAY_TICKS = 3L;

    private final UnlimitedNameTags plugin;
    private final Map<PassengerPacketKey, MyScheduledTask> pendingPassengerPackets = new ConcurrentHashMap<>();

    public BukkitNametagRuntime(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    private record PassengerPacketKey(@NotNull UUID viewerId, @NotNull UUID ownerId) {
    }

    @Override
    public int nextEntityId() {
        return plugin.getPacketManager().getEntityIndex();
    }

    @Override
    @NotNull
    public Settings settings() {
        return plugin.getConfigManager().getSettings();
    }

    @Override
    public boolean isNametagDebug() {
        return plugin.getNametagManager().isDebug();
    }

    @Override
    public void logInfo(@NotNull String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarning(@NotNull String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void logWarning(@NotNull String message, @NotNull Throwable error) {
        plugin.getLogger().log(Level.WARNING, message, error);
    }

    @Override
    public @Nullable CustomDisplayAnimationHandler resolveCustomAnimationHandler(@NotNull String id) {
        final var handler = plugin.getNametagCustomAnimationHandler(id);
        if (handler == null) {
            return null;
        }
        return (target, animation, scaledElapsedSeconds) -> {
            if (target instanceof org.alexdev.unlimitednametags.api.NametagAnimationTarget animationTarget) {
                handler.apply(animationTarget, animation, scaledElapsedSeconds);
            }
        };
    }

    @Override
    public @Nullable GlowOverride resolveGlowAnimation(@NotNull String id) {
        return plugin.getNametagGlowAnimation(id);
    }

    @Override
    @NotNull
    public Set<String> registeredGlowAnimationIds() {
        return plugin.getNametagGlowAnimationIds();
    }

    @Override
    public @Nullable CustomGlowHandler resolveCustomGlowHandler(@NotNull String id) {
        final NametagCustomGlowHandler handler = plugin.getNametagCustomGlowHandler(id);
        if (handler == null) {
            return null;
        }
        return context -> {
            final NametagCustomGlowContext paperContext = new NametagCustomGlowContext(
                    context.glow(),
                    context.scaledElapsedSeconds(),
                    context.monotonicTick(),
                    context.effectiveGlowTickInterval(),
                    context.ownerId());
            return handler.apply(paperContext);
        };
    }

    @Override
    @NotNull
    public Set<String> registeredCustomGlowHandlerIds() {
        return plugin.getNametagCustomGlowHandlerIds();
    }

    @Override
    public void runTaskLaterAsync(@NotNull Runnable task, long delayTicks) {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(task, delayTicks);
    }

    @Override
    public void schedulePassengersPacket(@NotNull UUID viewerId, @NotNull UUID ownerId) {
        final PassengerPacketKey key = new PassengerPacketKey(viewerId, ownerId);
        final MyScheduledTask task = plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            pendingPassengerPackets.remove(key);
            if (!canSendPassengersPacket(viewerId, ownerId)) {
                return;
            }
            final User viewerUser = packetUser(viewerId);
            if (viewerUser == null) {
                return;
            }
            sendPassengersPacket(viewerUser, ownerId);
        }, PASSENGER_PACKET_DELAY_TICKS);

        final MyScheduledTask previous = pendingPassengerPackets.put(key, task);
        if (previous != null) {
            previous.cancel();
        }
    }

    private boolean canSendPassengersPacket(@NotNull UUID viewerId, @NotNull UUID ownerId) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (viewer == null || owner == null || !viewer.isOnline() || !owner.isOnline()) {
            return false;
        }
        if (viewer.getWorld() != owner.getWorld()) {
            return false;
        }
        if (!viewer.canSee(owner)) {
            return false;
        }
        if (viewerId.equals(ownerId)) {
            return plugin.getNametagManager().isEffectiveShowOwnNametag(owner) && hasVisibleNametagRow(owner, viewer);
        }
        if (!owner.getTrackedBy().contains(viewer)) {
            return false;
        }
        if (!plugin.getTrackerManager().getTrackedPlayers(viewerId).contains(ownerId)) {
            return false;
        }
        return hasVisibleNametagRow(owner, viewer);
    }

    private boolean hasVisibleNametagRow(@NotNull Player owner, @NotNull Player viewer) {
        for (PaperNametagRow tag : plugin.getNametagManager().getPacketDisplays(owner)) {
            if (tag.canPlayerSee(viewer)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable User packetUser(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return null;
        }
        if (com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getChannel(player) == null) {
            return null;
        }
        return com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getUser(player);
    }

    @Override
    public float scaledDisplayScale(@NotNull UUID ownerId, float displayGroupScale) {
        final Player player = plugin.getPlayerListener().getPlayer(ownerId);
        if (player == null) {
            return displayGroupScale;
        }
        return plugin.getNametagManager().getScaledDisplayScale(player, displayGroupScale);
    }

    @Override
    public void removePassenger(@NotNull UUID viewerId, int displayEntityId) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        if (viewer != null) {
            plugin.getPacketManager().removePassenger(viewer, displayEntityId);
        } else {
            plugin.getPacketManager().removePassenger(displayEntityId);
        }
    }

    @Override
    public void removePassengerFromAll(int displayEntityId) {
        plugin.getPacketManager().removePassenger(displayEntityId);
    }

    @Override
    public void sendPassengersPacket(@NotNull User viewerUser, @NotNull UUID ownerId) {
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (owner == null) {
            return;
        }
        plugin.getNametagManager().sendPassengersPacket(viewerUser, owner);
    }

    @Override
    public boolean isDisplayGroupActive(@NotNull UUID ownerId, @NotNull Settings.DisplayGroup group) {
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (owner == null) {
            return false;
        }
        return plugin.getPlaceholderManager().isDisplayGroupActive(owner, group);
    }

    @Override
    @NotNull
    public String expandPlaceholdersForOwner(@NotNull UUID ownerId, @NotNull String raw) {
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (owner == null) {
            return raw;
        }
        return plugin.getPlaceholderManager().expandForOwner(owner, raw);
    }
}

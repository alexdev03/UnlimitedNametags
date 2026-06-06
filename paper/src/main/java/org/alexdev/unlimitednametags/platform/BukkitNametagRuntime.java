package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.protocol.player.User;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.api.NametagCustomGlowContext;
import org.alexdev.unlimitednametags.api.NametagCustomGlowHandler;
import org.alexdev.unlimitednametags.packet.CustomDisplayAnimationHandler;
import org.alexdev.unlimitednametags.packet.CustomGlowHandler;
import org.alexdev.unlimitednametags.packet.GlowApplyContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class BukkitNametagRuntime implements NametagRuntime {

    private final UnlimitedNameTags plugin;

    public BukkitNametagRuntime(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
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

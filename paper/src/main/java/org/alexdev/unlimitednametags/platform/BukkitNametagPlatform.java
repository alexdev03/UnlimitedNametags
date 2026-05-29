package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class BukkitNametagPlatform implements NametagPlatformBridge {

    private final UnlimitedNameTags plugin;
    private final UUID ownerId;

    public BukkitNametagPlatform(@NotNull UnlimitedNameTags plugin, @NotNull UUID ownerId) {
        this.plugin = plugin;
        this.ownerId = ownerId;
    }

    @Override
    @NotNull
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public boolean isOwnerOnline() {
        return plugin.getPlayerListener().getPlayer(ownerId) != null;
    }

    @Override
    public @Nullable Location anchorLocation(@NotNull UUID owner) {
        final Player player = plugin.getPlayerListener().getPlayer(owner);
        if (player == null) {
            return null;
        }
        final org.bukkit.Location bukkit = player.getLocation();
        bukkit.setPitch(0);
        bukkit.setYaw(-180);
        return SpigotConversionUtil.fromBukkitLocation(bukkit);
    }

    @Override
    public @Nullable User resolveUser(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return null;
        }
        if (PacketEvents.getAPI().getPlayerManager().getChannel(player) == null) {
            return null;
        }
        return PacketEvents.getAPI().getPlayerManager().getUser(player);
    }

    @Override
    public double distanceSquaredSameWorld(@NotNull UUID a, @NotNull UUID b) {
        final Player pa = plugin.getPlayerListener().getPlayer(a);
        final Player pb = plugin.getPlayerListener().getPlayer(b);
        if (pa == null || pb == null || pa.getWorld() != pb.getWorld()) {
            return -1;
        }
        return pa.getLocation().distanceSquared(pb.getLocation());
    }

    @Override
    public boolean isSneaking(@NotNull UUID owner) {
        final Player player = plugin.getPlayerListener().getPlayer(owner);
        return player != null && player.isSneaking();
    }

    @Override
    public float resolveDisplayScale(@NotNull UUID owner, float configScale) {
        final Player player = plugin.getPlayerListener().getPlayer(owner);
        if (player == null) {
            return configScale;
        }
        if (!plugin.getNametagManager().isScalePresent()) {
            return configScale;
        }
        final AttributeInstance attribute = player.getAttribute(plugin.getNametagManager().getScaleAttribute());
        if (attribute == null) {
            return configScale;
        }
        return (float) (attribute.getValue() * configScale);
    }

    @Override
    public boolean viewerSupportsTextDisplay(@NotNull UUID viewerId) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        if (viewer == null) {
            return false;
        }
        return plugin.getHook(ViaVersionHook.class).map(h -> !h.hasNotTextDisplays(viewer)).orElse(true);
    }

    @Override
    public boolean isEligibleToShow(@NotNull UUID owner, @NotNull UUID viewerId, boolean visible, boolean viewerAlreadySeeing) {
        if (!visible) {
            return false;
        }
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Player ownerPlayer = plugin.getPlayerListener().getPlayer(owner);
        if (viewer == null || ownerPlayer == null) {
            return false;
        }
        if (!viewerSupportsTextDisplay(viewerId)) {
            return false;
        }
        if (!viewer.canSee(ownerPlayer)) {
            return false;
        }
        if (viewer.getWorld() != ownerPlayer.getWorld()) {
            return false;
        }
        if (resolveUser(viewerId) == null || resolveUser(owner) == null) {
            return false;
        }
        final boolean isOwnerViewer = viewerId.equals(owner);
        if (!isOwnerViewer && !viewer.hasPermission("unt.shownametags")) {
            return false;
        }
        if (isOwnerViewer && !viewer.hasPermission("unt.showownnametag")) {
            return false;
        }
        if (plugin.getNametagManager().isHiddenOtherNametags(viewer)) {
            return false;
        }
        if (!isOwnerViewer && plugin.getNametagManager().isHidingOwnNametagFromOthers(ownerPlayer)) {
            return false;
        }
        if (plugin.getConfigManager().getSettings().getVisibility().isShowWhileLooking()
                && !plugin.getNametagManager().isPlayerPointingAt(viewer, ownerPlayer)) {
            return false;
        }
        if (isOwnerViewer && plugin.getNametagManager().isEffectiveShowOwnNametag(ownerPlayer)) {
            return true;
        }
        return !viewerAlreadySeeing;
    }

    @Override
    public @Nullable String playerName(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        return player != null ? player.getName() : null;
    }

    @Override
    public boolean isEffectiveShowOwnNametag(@NotNull UUID owner) {
        final Player player = plugin.getPlayerListener().getPlayer(owner);
        return player != null && plugin.getNametagManager().isEffectiveShowOwnNametag(player);
    }

    @Override
    public @Nullable Location offsetDisplayLocation(float displayScale) {
        final Player player = plugin.getPlayerListener().getPlayer(ownerId);
        if (player == null) {
            return null;
        }
        final org.bukkit.Location bukkit = player.getLocation();
        bukkit.setPitch(0);
        bukkit.setYaw(-180);
        bukkit.setY(bukkit.getY() + 1.8 * displayScale);
        return SpigotConversionUtil.fromBukkitLocation(bukkit);
    }

    @Override
    public boolean viewerLacksTextDisplaySupport(@NotNull UUID viewerId) {
        return !viewerSupportsTextDisplay(viewerId);
    }

    @Override
    public boolean hasLineOfSight(@NotNull UUID viewerId, @NotNull UUID owner) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Player ownerPlayer = plugin.getPlayerListener().getPlayer(owner);
        if (viewer == null || ownerPlayer == null) {
            return false;
        }
        return viewer.hasLineOfSight(ownerPlayer);
    }
}

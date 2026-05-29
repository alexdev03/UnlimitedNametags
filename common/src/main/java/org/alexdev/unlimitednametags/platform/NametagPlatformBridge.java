package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Resolves online players, worlds, and visibility rules without Bukkit types in callers.
 */
public interface NametagPlatformBridge {

    @NotNull
    UUID ownerId();

    boolean isOwnerOnline();

    @Nullable
    Location anchorLocation(@NotNull UUID ownerId);

    @Nullable
    User resolveUser(@NotNull UUID playerId);

    /**
     * @return squared distance when both are in the same world, otherwise {@code -1}
     */
    double distanceSquaredSameWorld(@NotNull UUID a, @NotNull UUID b);

    boolean isSneaking(@NotNull UUID ownerId);

    float resolveDisplayScale(@NotNull UUID ownerId, float configScale);

    boolean viewerSupportsTextDisplay(@NotNull UUID viewerId);

    boolean isEligibleToShow(@NotNull UUID ownerId, @NotNull UUID viewerId, boolean visible, boolean viewerAlreadySeeing);

    @Nullable
    String playerName(@NotNull UUID playerId);

    boolean isEffectiveShowOwnNametag(@NotNull UUID ownerId);

    /**
     * Anchor location with pitch 0, yaw -180, and Y raised by {@code 1.8 * displayScale}.
     */
    @Nullable
    Location offsetDisplayLocation(float displayScale);

    boolean viewerLacksTextDisplaySupport(@NotNull UUID viewerId);

    boolean hasLineOfSight(@NotNull UUID viewerId, @NotNull UUID ownerId);
}

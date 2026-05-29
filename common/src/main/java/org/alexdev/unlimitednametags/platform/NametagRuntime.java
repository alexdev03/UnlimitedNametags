package org.alexdev.unlimitednametags.platform;

import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.CustomDisplayAnimationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral services for nametag display entities (entity ids, config, logging, scheduling).
 */
public interface NametagRuntime {

    int nextEntityId();

    @NotNull
    Settings settings();

    boolean isNametagDebug();

    void logInfo(@NotNull String message);

    void logWarning(@NotNull String message);

    void logWarning(@NotNull String message, @NotNull Throwable error);

    @Nullable
    CustomDisplayAnimationHandler resolveCustomAnimationHandler(@NotNull String id);

    void runTaskLaterAsync(@NotNull Runnable task, long delayTicks);

    float scaledDisplayScale(@NotNull java.util.UUID ownerId, float displayGroupScale);

    void removePassenger(@NotNull java.util.UUID viewerId, int displayEntityId);

    void removePassengerFromAll(int displayEntityId);

    void sendPassengersPacket(@NotNull com.github.retrooper.packetevents.protocol.player.User viewerUser,
                              @NotNull java.util.UUID ownerId);

    boolean isDisplayGroupActive(@NotNull java.util.UUID ownerId, @NotNull Settings.DisplayGroup group);

    @NotNull
    String expandPlaceholdersForOwner(@NotNull java.util.UUID ownerId, @NotNull String raw);
}

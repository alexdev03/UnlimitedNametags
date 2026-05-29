package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.Settings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Nametag operations (platform-neutral, keyed by player UUID).
 * Paper overloads: {@code UntNametagManagerPaper} (api-paper module).
 */
@SuppressWarnings("unused")
public interface UntNametagManager {

    boolean isScalePresent();

    float getScale(@NotNull UUID playerId);

    void blockPlayer(@NotNull UUID playerId);

    void unblockPlayer(@NotNull UUID playerId);

    void clearCache(@NotNull UUID playerId);

    boolean hasNametagOverride(@NotNull UUID playerId);

    @NotNull
    Optional<Settings.NameTag> getNametagOverride(@NotNull UUID playerId);

    @NotNull
    Settings.NameTag getEffectiveNametag(@NotNull UUID playerId);

    @NotNull
    Settings.NameTag getConfigNametag(@NotNull UUID playerId);

    void addPlayer(@NotNull UUID playerId, boolean canBlock);

    void refresh(@NotNull UUID playerId, boolean force);

    void removePlayer(@NotNull UUID playerId);

    void removeAllViewers(@NotNull UUID playerId);

    void showToTrackedPlayers(@NotNull UUID playerId);

    void showToTrackedPlayers(@NotNull UUID playerId, @NotNull Collection<UUID> tracked);

    void hideAllDisplays(@NotNull UUID playerId);

    void removeAll();

    void updateSneaking(@NotNull UUID playerId, boolean sneaking);

    void reload();

    void vanishPlayer(@NotNull UUID playerId);

    void unVanishPlayer(@NotNull UUID playerId);

    @NotNull
    Optional<? extends UntNametagDisplayCore> getPacketDisplayText(int entityId);

    void updateDisplay(@NotNull UUID ownerId, @NotNull UUID targetId);

    void showToOwner(@NotNull UUID ownerId);

    void removeDisplay(@NotNull UUID ownerId, @NotNull UUID targetId);

    void updateDisplaysForPlayer(@NotNull UUID playerId);

    void refreshDisplaysForPlayer(@NotNull UUID playerId);

    void unBlockForAllPlayers(@NotNull UUID playerId);

    void hideOtherNametags(@NotNull UUID playerId);

    void showOtherNametags(@NotNull UUID playerId);

    boolean isHiddenOtherNametags(@NotNull UUID playerId);

    boolean isEffectiveShowOwnNametag(@NotNull UUID playerId);

    boolean isShowingOwnNametagToSelf(@NotNull UUID playerId);

    void setShowingOwnNametagToSelf(@NotNull UUID playerId, boolean show);

    boolean isShowingOwnNametagToOthers(@NotNull UUID playerId);

    void setShowingOwnNametagToOthers(@NotNull UUID playerId, boolean show);

    void applyPreferencesFromPersistentData(@NotNull UUID playerId);

    void syncPlayerPreferenceSetsFromPdc(@NotNull UUID playerId);

    void swapNametag(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag);

    void setNametagOverride(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag);

    void removeNametagOverride(@NotNull UUID playerId);

    void setShiftSystemBlocked(@NotNull UUID playerId, boolean blocked);

    boolean isShiftSystemBlocked(@NotNull UUID playerId);

    boolean isDebug();

    void setDebug(boolean debug);
}

package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Nametag operations exposed to {@link UNTAPI} and integrators.
 */
@SuppressWarnings("unused")
public interface UntNametagManager {

    boolean isPlayerPointingAt(Player player1, Player player2);

    boolean isScalePresent();

    float getScale(@NotNull Player player);

    void blockPlayer(@NotNull Player player);

    void unblockPlayer(@NotNull Player player);

    void clearCache(@NotNull UUID uuid);

    boolean hasNametagOverride(@NotNull Player player);

    @NotNull
    Optional<Settings.NameTag> getNametagOverride(@NotNull Player player);

    @NotNull
    Settings.NameTag getEffectiveNametag(@NotNull Player player);

    @NotNull
    Settings.NameTag getConfigNametag(@NotNull Player player);

    void addPlayer(@NotNull Player player, boolean canBlock);

    void refresh(@NotNull Player player, boolean force);

    void removePlayer(@NotNull Player player);

    void removeAllViewers(@NotNull Player player);

    void showToTrackedPlayers(@NotNull Player player);

    void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<Player> tracked);

    void hideAllDisplays(@NotNull Player player);

    void removeAll();

    void updateSneaking(@NotNull Player player, boolean sneaking);

    void reload();

    void debug(@NotNull CommandSender audience);

    void vanishPlayer(@NotNull Player player);

    void unVanishPlayer(@NotNull Player player);

    @NotNull
    Collection<? extends UntNametagDisplay> getPacketDisplayText(@NotNull Player player);

    @NotNull
    Optional<? extends UntNametagDisplay> getPacketDisplayText(int id);

    void updateDisplay(@NotNull Player player, @NotNull Player target);

    void showToOwner(@NotNull Player player);

    void removeDisplay(@NotNull Player player, @NotNull Player target);

    void updateDisplaysForPlayer(@NotNull Player player);

    void refreshDisplaysForPlayer(@NotNull Player player);

    void unBlockForAllPlayers(@NotNull Player player);

    void hideOtherNametags(@NotNull Player player);

    void showOtherNametags(@NotNull Player player);

    boolean isHiddenOtherNametags(@NotNull Player player);

    boolean isEffectiveShowOwnNametag(@NotNull Player player);

    boolean isShowingOwnNametagToSelf(@NotNull Player player);

    void setShowingOwnNametagToSelf(@NotNull Player player, boolean show);

    boolean isShowingOwnNametagToOthers(@NotNull Player player);

    void setShowingOwnNametagToOthers(@NotNull Player player, boolean show);

    void applyPreferencesFromPersistentData(@NotNull Player player);

    /**
     * Loads nametag UI preferences from the player's PDC into runtime sets only (no packets).
     * Call as early as possible on join so {@link #isHiddenOtherNametags} and related checks are correct
     * before any nametag packets are sent to this player.
     */
    void syncPlayerPreferenceSetsFromPdc(@NotNull Player player);

    void swapNametag(@NotNull Player player, @NotNull Settings.NameTag nameTag);

    void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag);

    void removeNametagOverride(@NotNull Player player);

    void setShiftSystemBlocked(@NotNull Player player, boolean blocked);

    boolean isShiftSystemBlocked(@NotNull Player player);

    boolean isDebug();

    void setDebug(boolean debug);

    Attribute getScaleAttribute();
}

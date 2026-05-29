package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paper/Bukkit {@link UntNametagManager} with {@link Player} convenience overloads.
 */
@SuppressWarnings("unused")
public interface UntNametagManagerPaper extends UntNametagManager {

    boolean isPlayerPointingAt(@NotNull Player player1, @NotNull Player player2);

    @NotNull
    Collection<? extends UntNametagDisplay> getPacketDisplayText(@NotNull Player player);

    void debug(@NotNull CommandSender audience);

    @NotNull
    Attribute getScaleAttribute();

    default void blockPlayer(@NotNull Player player) {
        blockPlayer(player.getUniqueId());
    }

    default void unblockPlayer(@NotNull Player player) {
        unblockPlayer(player.getUniqueId());
    }

    default boolean hasNametagOverride(@NotNull Player player) {
        return hasNametagOverride(player.getUniqueId());
    }

    @NotNull
    default Optional<Settings.NameTag> getNametagOverride(@NotNull Player player) {
        return getNametagOverride(player.getUniqueId());
    }

    @NotNull
    default Settings.NameTag getEffectiveNametag(@NotNull Player player) {
        return getEffectiveNametag(player.getUniqueId());
    }

    @NotNull
    default Settings.NameTag getConfigNametag(@NotNull Player player) {
        return getConfigNametag(player.getUniqueId());
    }

    default void addPlayer(@NotNull Player player, boolean canBlock) {
        addPlayer(player.getUniqueId(), canBlock);
    }

    default void refresh(@NotNull Player player, boolean force) {
        refresh(player.getUniqueId(), force);
    }

    default void removePlayer(@NotNull Player player) {
        removePlayer(player.getUniqueId());
    }

    default void removeAllViewers(@NotNull Player player) {
        removeAllViewers(player.getUniqueId());
    }

    default void showToTrackedPlayers(@NotNull Player player) {
        showToTrackedPlayers(player.getUniqueId());
    }

    default void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<Player> tracked) {
        showToTrackedPlayers(player.getUniqueId(), tracked.stream().map(Player::getUniqueId).collect(Collectors.toList()));
    }

    default void hideAllDisplays(@NotNull Player player) {
        hideAllDisplays(player.getUniqueId());
    }

    default void updateSneaking(@NotNull Player player, boolean sneaking) {
        updateSneaking(player.getUniqueId(), sneaking);
    }

    default void vanishPlayer(@NotNull Player player) {
        vanishPlayer(player.getUniqueId());
    }

    default void unVanishPlayer(@NotNull Player player) {
        unVanishPlayer(player.getUniqueId());
    }

    default void updateDisplay(@NotNull Player owner, @NotNull Player target) {
        updateDisplay(owner.getUniqueId(), target.getUniqueId());
    }

    default void showToOwner(@NotNull Player owner) {
        showToOwner(owner.getUniqueId());
    }

    default void removeDisplay(@NotNull Player owner, @NotNull Player target) {
        removeDisplay(owner.getUniqueId(), target.getUniqueId());
    }

    default void updateDisplaysForPlayer(@NotNull Player player) {
        updateDisplaysForPlayer(player.getUniqueId());
    }

    default void refreshDisplaysForPlayer(@NotNull Player player) {
        refreshDisplaysForPlayer(player.getUniqueId());
    }

    default void unBlockForAllPlayers(@NotNull Player player) {
        unBlockForAllPlayers(player.getUniqueId());
    }

    default void hideOtherNametags(@NotNull Player player) {
        hideOtherNametags(player.getUniqueId());
    }

    default void showOtherNametags(@NotNull Player player) {
        showOtherNametags(player.getUniqueId());
    }

    default boolean isHiddenOtherNametags(@NotNull Player player) {
        return isHiddenOtherNametags(player.getUniqueId());
    }

    default boolean isEffectiveShowOwnNametag(@NotNull Player player) {
        return isEffectiveShowOwnNametag(player.getUniqueId());
    }

    default boolean isShowingOwnNametagToSelf(@NotNull Player player) {
        return isShowingOwnNametagToSelf(player.getUniqueId());
    }

    default void setShowingOwnNametagToSelf(@NotNull Player player, boolean show) {
        setShowingOwnNametagToSelf(player.getUniqueId(), show);
    }

    default boolean isShowingOwnNametagToOthers(@NotNull Player player) {
        return isShowingOwnNametagToOthers(player.getUniqueId());
    }

    default void setShowingOwnNametagToOthers(@NotNull Player player, boolean show) {
        setShowingOwnNametagToOthers(player.getUniqueId(), show);
    }

    default void applyPreferencesFromPersistentData(@NotNull Player player) {
        applyPreferencesFromPersistentData(player.getUniqueId());
    }

    default void syncPlayerPreferenceSetsFromPdc(@NotNull Player player) {
        syncPlayerPreferenceSetsFromPdc(player.getUniqueId());
    }

    default void swapNametag(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        swapNametag(player.getUniqueId(), nameTag);
    }

    default void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        setNametagOverride(player.getUniqueId(), nameTag);
    }

    default void removeNametagOverride(@NotNull Player player) {
        removeNametagOverride(player.getUniqueId());
    }

    default void setShiftSystemBlocked(@NotNull Player player, boolean blocked) {
        setShiftSystemBlocked(player.getUniqueId(), blocked);
    }

    default boolean isShiftSystemBlocked(@NotNull Player player) {
        return isShiftSystemBlocked(player.getUniqueId());
    }

    default float getScale(@NotNull Player player) {
        return getScale(player.getUniqueId());
    }
}

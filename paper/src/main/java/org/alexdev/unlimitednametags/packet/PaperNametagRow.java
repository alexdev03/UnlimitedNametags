package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.util.Quaternion4f;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.NametagAnimationTarget;
import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.jetbrains.annotations.Nullable;
import org.alexdev.unlimitednametags.api.event.PlayerNametagHideEvent;
import org.alexdev.unlimitednametags.api.event.PlayerNametagLifecycleEvent;
import org.alexdev.unlimitednametags.api.event.PlayerNametagRefreshEvent;
import org.alexdev.unlimitednametags.api.event.PlayerNametagShowEvent;
import org.alexdev.unlimitednametags.api.event.PlayerNametagVisibilityEvent;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Paper/Bukkit-facing {@link PacketNameTag} with {@link Player} convenience methods and public API interfaces.
 * All implementors extend {@link PacketNameTag}.
 */
public interface PaperNametagRow extends UntNametagDisplay, NametagAnimationTarget {

    @NotNull
    UnlimitedNameTags getPlugin();

    private @NotNull PacketNameTag packet() {
        return (PacketNameTag) this;
    }

    @Override
    @NotNull
    default Player getOwner() {
        return getPlugin().getPlayerListener().getPlayer(packet().getOwnerId());
    }

    default boolean text(@NotNull Player player, @NotNull Component text) {
        return packet().text(player.getUniqueId(), text);
    }

    default void setBackgroundColor(@NotNull Color color) {
        packet().setBackgroundColor(color.asARGB());
    }

    @Override
    default void setGlowOverride(@Nullable GlowOverride glow) {
        packet().setGlowOverride(glow);
        packet().applyGlowNow(0L);
    }

    @Override
    default void clearGlowOverride() {
        packet().setGlowOverride(null);
        packet().clearGlow();
    }

    @Override
    default void hideFromPlayer(@NotNull Player player) {
        final boolean visible = fireVisibilityEvent(getOwner(), player, false, canPlayerSee(player));
        if (visible) {
            return;
        }
        packet().hideFromViewer(player.getUniqueId());
        fireLifecycleEvent(new PlayerNametagHideEvent(getOwner(), player, this, !getPlugin().getServer().isPrimaryThread()));
    }

    @Override
    default void showToPlayer(@NotNull Player player) {
        final boolean visible = fireVisibilityEvent(getOwner(), player, true, canPlayerSee(player));
        if (!visible) {
            if (getPlugin().getNametagManager().isDebug()) {
                getPlugin().getLogger().info("Visibility event prevented showing nametag of "
                        + getOwner().getName() + " to " + player.getName());
            }
            return;
        }
        packet().showToViewer(player.getUniqueId());
        fireLifecycleEvent(new PlayerNametagShowEvent(getOwner(), player, this, !getPlugin().getServer().isPrimaryThread()));
    }

    @Override
    default void showToPlayers(@NotNull Set<Player> players) {
        for (Player player : players) {
            showToPlayer(player);
        }
    }

    default void hideFromPlayerSilently(@NotNull Player player) {
        packet().hideFromViewerSilently(player.getUniqueId());
    }

    default boolean canPlayerSee(@NotNull Player player) {
        return packet().canViewerSee(player.getUniqueId());
    }

    default void spawn(@NotNull Player player) {
        packet().spawnViewer(player.getUniqueId());
    }

    default void handleQuit(@NotNull Player player) {
        packet().handleQuit(player.getUniqueId());
    }

    @Override
    default void setLocalPose(
            float translationX,
            float translationY,
            float translationZ,
            float quaternionX,
            float quaternionY,
            float quaternionZ,
            float quaternionW,
            float scaleMultiplier) {
        packet().setAnimationPose(
                translationX,
                translationY,
                translationZ,
                new Quaternion4f(quaternionX, quaternionY, quaternionZ, quaternionW),
                scaleMultiplier);
    }

    @Override
    default void clearLocalPose() {
        packet().clearAnimationPose();
    }

    @Override
    default void refreshForViewer(@NotNull UUID viewerId) {
        refreshForViewer(viewerId, false);
    }

    @Override
    default void refreshForViewer(@NotNull UUID viewerId, final boolean force) {
        if (packet().flushViewerMetadata(viewerId, force)) {
            notifyRefreshedForViewer(viewerId);
        }
    }

    default void notifyRefreshedForViewer(@NotNull UUID viewerId) {
        final Player viewer = getPlugin().getPlayerListener().getPlayer(viewerId);
        if (viewer == null) {
            return;
        }
        notifyRefreshedForPlayer(viewer);
    }

    default void notifyRefreshedForPlayer(@NotNull Player player) {
        fireLifecycleEvent(new PlayerNametagRefreshEvent(getOwner(), player, this, !getPlugin().getServer().isPrimaryThread()));
    }

    default void refreshForPlayer(@NotNull Player player, final boolean force) {
        refreshForViewer(player.getUniqueId(), force);
    }

    @Override
    default void refreshAllViewers(final boolean force) {
        packet().flushAllViewers(force);
    }


    private void fireLifecycleEvent(@NotNull PlayerNametagLifecycleEvent event) {
        getPlugin().getServer().getPluginManager().callEvent(event);
    }

    private boolean fireVisibilityEvent(
            @NotNull Player owner,
            @NotNull Player viewer,
            boolean shouldBeVisible,
            boolean viewerAlreadySeeing
    ) {
        final PlayerNametagVisibilityEvent event = new PlayerNametagVisibilityEvent(
                owner,
                viewer,
                shouldBeVisible,
                viewerAlreadySeeing,
                !getPlugin().getServer().isPrimaryThread()
        );
        getPlugin().getServer().getPluginManager().callEvent(event);
        return event.isVisible();
    }
}

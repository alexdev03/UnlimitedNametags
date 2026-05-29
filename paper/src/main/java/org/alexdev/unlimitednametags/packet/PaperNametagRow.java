package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.util.Quaternion4f;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.NametagAnimationTarget;
import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
}

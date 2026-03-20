package org.alexdev.unlimitednametags.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Writable pose for one nametag display row during animation ticks.
 * Implementations are internal; obtain only from {@link UntNametagDisplay} via {@code instanceof}.
 */
public interface NametagAnimationTarget {

    @NotNull
    Player getOwner();

    /**
     * Packet entity id of this display (not the owning player).
     */
    int getNametagDisplayEntityId();

    /**
     * Local translation (blocks) and left rotation quaternion (x, y, z, w) plus uniform scale multiplier
     * relative to the row's configured scale.
     */
    void setLocalPose(float translationX, float translationY, float translationZ,
                      float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
                      float scaleMultiplier);

    void clearLocalPose();
}

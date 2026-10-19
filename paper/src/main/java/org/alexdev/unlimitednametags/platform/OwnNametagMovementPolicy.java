package org.alexdev.unlimitednametags.platform;

import org.bukkit.entity.Pose;

/** Bukkit pose translation for a temporary, owner-only presentation decision. */
public final class OwnNametagMovementPolicy {
    private OwnNametagMovementPolicy() {}

    public static boolean isHorizontalPose(Pose pose) {
        return pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK;
    }
}

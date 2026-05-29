package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.util.Quaternion4f;
import org.jetbrains.annotations.NotNull;

/**
 * Writable animation pose for one nametag display row (platform-neutral).
 */
public interface AnimationPoseTarget {

  int getNametagDisplayEntityId();

  long getDvdRandomSeed();

  void clearAnimationPose();

  void setAnimationPose(float tx, float ty, float tz, @NotNull Quaternion4f q, float scaleMul);

  boolean isAnimDvdInitialized();

  void setAnimDvdInitialized(boolean initialized);

  float getAnimDvdX();

  void setAnimDvdX(float x);

  float getAnimDvdZ();

  void setAnimDvdZ(float z);

  float getAnimDvdVx();

  void setAnimDvdVx(float vx);

  float getAnimDvdVz();

  void setAnimDvdVz(float vz);
}

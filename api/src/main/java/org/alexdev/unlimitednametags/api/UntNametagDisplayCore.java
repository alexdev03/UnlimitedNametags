package org.alexdev.unlimitednametags.api;

import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Platform-neutral display row API (viewer ids as UUID). Paper extensions: {@code UntNametagDisplay} (api-paper module).
 */
public interface UntNametagDisplayCore {

    void setBillboard(@NotNull AbstractDisplayMeta.BillboardConstraints billboard);

    void refresh();

    void setForcedNameTag(@NotNull Component component);

    void setForcedNameTag(@NotNull UUID viewerId, @NotNull Component component);

    void clearForcedNameTag();

    void clearForcedNameTag(@NotNull UUID viewerId);

    void refreshForViewer(@NotNull UUID viewerId);

    void showToViewer(@NotNull UUID viewerId);

    void hideFromViewer(@NotNull UUID viewerId);

    void showToViewers(@NotNull Set<UUID> viewerIds);

    void showForOwner();

    void hideForOwner();

    boolean isSneaking();
}

package org.alexdev.unlimitednametags.api.event;

import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for Paper nametag lifecycle events with owner/viewer and row references.
 */
public abstract class PlayerNametagLifecycleEvent extends Event {

    private final Player owner;
    private final Player viewer;
    private final UntNametagDisplay display;
    private final boolean ownerIsViewer;

    protected PlayerNametagLifecycleEvent(
            @NotNull Player owner,
            @NotNull Player viewer,
            @NotNull UntNametagDisplay display,
            boolean isAsync
    ) {
        super(isAsync);
        this.owner = owner;
        this.viewer = viewer;
        this.display = display;
        this.ownerIsViewer = owner.getUniqueId().equals(viewer.getUniqueId());
    }

    @NotNull
    public Player getOwner() {
        return owner;
    }

    @NotNull
    public Player getViewer() {
        return viewer;
    }

    @NotNull
    public UntNametagDisplay getDisplay() {
        return display;
    }

    public boolean isOwnerViewingOwnNametag() {
        return ownerIsViewer;
    }
}

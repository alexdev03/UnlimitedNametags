package org.alexdev.unlimitednametags.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Emitted whenever a player nametag visibility decision is being evaluated for another player.
 * Plugins can mutate the final visibility value before packets are sent.
 */
public final class PlayerNametagVisibilityEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player owner;
    private final Player viewer;
    private final boolean ownerIsViewer;
    private final boolean viewerAlreadySeeing;
    private boolean visible;

    public PlayerNametagVisibilityEvent(
            @NotNull Player owner,
            @NotNull Player viewer,
            boolean visible,
            boolean viewerAlreadySeeing,
            boolean isAsync
    ) {
        super(isAsync);
        this.owner = owner;
        this.viewer = viewer;
        this.visible = visible;
        this.ownerIsViewer = owner.getUniqueId().equals(viewer.getUniqueId());
        this.viewerAlreadySeeing = viewerAlreadySeeing;
    }

    @NotNull
    public Player getOwner() {
        return owner;
    }

    @NotNull
    public Player getViewer() {
        return viewer;
    }

    public boolean isOwnerViewingOwnNametag() {
        return ownerIsViewer;
    }

    public boolean isViewerAlreadySeeing() {
        return viewerAlreadySeeing;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

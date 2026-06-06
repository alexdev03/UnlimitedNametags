package org.alexdev.unlimitednametags.api.event;

import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a row is shown to a viewer.
 */
public final class PlayerNametagShowEvent extends PlayerNametagLifecycleEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerNametagShowEvent(
            @NotNull Player owner,
            @NotNull Player viewer,
            @NotNull UntNametagDisplay display,
            boolean isAsync
    ) {
        super(owner, viewer, display, isAsync);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

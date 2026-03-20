package org.alexdev.unlimitednametags.api;

import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Public view of a per-line nametag display entity (text, item, or block display).
 */
public interface UntNametagDisplay {

    void setBillboard(@NotNull AbstractDisplayMeta.BillboardConstraints billboard);

    void refresh();

    void setForcedNameTag(@NotNull Component component);

    void setForcedNameTag(@NotNull UUID viewer, @NotNull Component component);

    void clearForcedNameTag();

    void clearForcedNameTag(@NotNull UUID viewer);

    void refreshForPlayer(@NotNull Player player);

    void showToPlayer(@NotNull Player player);

    void hideFromPlayer(@NotNull Player player);

    void showToPlayers(@NotNull Set<Player> players);

    void showForOwner();

    void hideForOwner();

    boolean isSneaking();
}

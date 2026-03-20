package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.google.common.collect.Maps;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Per-viewer text display state and updates for {@link TextPacketNameTag} only.
 * Item/block displays do not use this type.
 */
final class TextNametagSupport {

    private record ViewerTextSnap(byte opacity, boolean seeThrough) {
    }

    private final TextPacketNameTag host;
    private final Map<UUID, Component> relationalCache = Maps.newConcurrentMap();
    private final Map<UUID, Component> calculatedTextCache = Maps.newConcurrentMap();
    private final Map<UUID, Component> forcedRelationalText = Maps.newConcurrentMap();
    private volatile Component forcedGlobalText;
    private final Map<UUID, ViewerTextSnap> obscuredPresentationByViewer = new ConcurrentHashMap<>();

    TextNametagSupport(@NotNull final TextPacketNameTag host) {
        this.host = host;
    }

    boolean text(@NotNull final Player player, @NotNull final Component text) {
        if (host.isRemoved()) {
            return false;
        }

        calculatedTextCache.put(player.getUniqueId(), text);

        if (forcedRelationalText.containsKey(player.getUniqueId()) || forcedGlobalText != null) {
            return false;
        }

        return applyForcedOrCachedText(player, text);
    }

    private boolean applyForcedOrCachedText(@NotNull final Player player, @NotNull final Component text) {
        if (text.equals(relationalCache.get(player.getUniqueId()))) {
            return false;
        }

        relationalCache.put(player.getUniqueId(), text);
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return false;
        }

        modifyTextForViewer(user, meta -> meta.setText(text));
        host.touchLastUpdate();
        return true;
    }

    void modifyTextForViewer(@Nullable final User user, @NotNull final Consumer<TextDisplayMeta> consumer) {
        if (host.isRemoved() || user == null) {
            return;
        }

        host.getPerPlayerEntity().modify(user, e -> {
            if (e == null) {
                return;
            }
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    void modifyTextForOwner(@NotNull final Consumer<TextDisplayMeta> consumer) {
        if (host.isRemoved()) {
            return;
        }

        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(host.getOwner());
        if (ownerUser == null) {
            return;
        }

        modifyTextForViewer(ownerUser, consumer);
    }

    void modifyTextAll(@NotNull final Consumer<TextDisplayMeta> consumer) {
        if (host.isRemoved()) {
            return;
        }

        host.getPerPlayerEntity().execute(e -> {
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    void setShadowed(final boolean shadowed) {
        modifyTextAll(meta -> meta.setShadow(shadowed));
    }

    void setSeeThrough(final boolean seeThrough) {
        modifyTextAll(meta -> meta.setSeeThrough(seeThrough));
    }

    void setBackgroundColor(@NotNull final Color color) {
        modifyTextAll(meta -> meta.setBackgroundColor(color.asARGB()));
    }

    void setTextOpacity(final byte b) {
        modifyTextAll(meta -> meta.setTextOpacity(b));
    }

    void clearObscuredPresentationTracking() {
        obscuredPresentationByViewer.clear();
    }

    void onViewerRemoved(@NotNull final UUID viewerId) {
        relationalCache.remove(viewerId);
        calculatedTextCache.remove(viewerId);
        obscuredPresentationByViewer.remove(viewerId);
    }

    void applyObscuredLineOfSightPresentation(
            final boolean featureEnabled,
            final byte sneakOpacityByte,
            final byte obscuredOpacityByte,
            final double maxDistanceSq,
            final boolean sneakEffective) {
        if (!featureEnabled) {
            return;
        }

        final Player owner = host.getOwner();
        final boolean groupSeeThrough = host.getDisplayGroup().effectiveBackground().seeThrough();
        final boolean baseSeeThrough = groupSeeThrough && !sneakEffective;

        for (final UUID vid : new ArrayList<>(host.getViewers())) {
            Player resolved = host.getPlugin().getPlayerListener().getPlayer(vid);
            if (resolved == null) {
                resolved = Bukkit.getPlayer(vid);
            }
            if (resolved == null) {
                continue;
            }
            final Player viewer = resolved;

            if (host.getPlugin().getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(viewer)).orElse(false)) {
                continue;
            }

            byte opacity = -1;
            boolean seeThroughMeta = baseSeeThrough;

            if (sneakEffective) {
                opacity = sneakOpacityByte;
            } else if (!viewer.getUniqueId().equals(owner.getUniqueId())) {
                if (viewer.getWorld() == owner.getWorld()
                        && viewer.getLocation().distanceSquared(owner.getLocation()) <= maxDistanceSq
                        && !viewer.hasLineOfSight(owner)) {
                    opacity = obscuredOpacityByte;
                    seeThroughMeta = true;
                }
            }

            final ViewerTextSnap prev = obscuredPresentationByViewer.get(viewer.getUniqueId());
            if (prev != null && prev.opacity() == opacity && prev.seeThrough() == seeThroughMeta) {
                continue;
            }
            obscuredPresentationByViewer.put(viewer.getUniqueId(), new ViewerTextSnap(opacity, seeThroughMeta));

            final User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
            if (user == null) {
                continue;
            }
            final byte opacityFinal = opacity;
            final boolean seeThroughFinal = seeThroughMeta;
            modifyTextForViewer(user, m -> {
                m.setTextOpacity(opacityFinal);
                m.setSeeThrough(seeThroughFinal);
            });
            host.refreshForPlayer(viewer);
        }
    }

    void setForcedGlobal(@NotNull final Component component) {
        this.forcedGlobalText = component;
        updateAllViewers();
    }

    void setForcedRelational(@NotNull final UUID viewer, @NotNull final Component component) {
        this.forcedRelationalText.put(viewer, component);
        updateViewer(viewer);
    }

    void clearForcedGlobal() {
        this.forcedGlobalText = null;
        updateAllViewers();
    }

    void clearForcedRelational(@NotNull final UUID viewer) {
        this.forcedRelationalText.remove(viewer);
        updateViewer(viewer);
    }

    private void updateAllViewers() {
        for (final UUID uuid : host.getViewers()) {
            updateViewer(uuid);
        }
    }

    /**
     * Re-applies cached or forced text when a viewer is (re)attached.
     */
    void refreshViewerIfCached(@NotNull final UUID uuid) {
        updateViewer(uuid);
    }

    private void updateViewer(@NotNull final UUID uuid) {
        final Player player = host.getPlugin().getPlayerListener().getPlayer(uuid);
        if (player == null) {
            return;
        }

        Component textToUse = calculatedTextCache.get(uuid);

        if (forcedRelationalText.containsKey(uuid)) {
            textToUse = forcedRelationalText.get(uuid);
        } else if (forcedGlobalText != null) {
            textToUse = forcedGlobalText;
        }

        if (textToUse != null) {
            if (applyForcedOrCachedText(player, textToUse)) {
                host.refreshForPlayer(player);
            }
        }
    }

    void dispose() {
        relationalCache.clear();
        calculatedTextCache.clear();
        forcedRelationalText.clear();
        forcedGlobalText = null;
        obscuredPresentationByViewer.clear();
    }
}

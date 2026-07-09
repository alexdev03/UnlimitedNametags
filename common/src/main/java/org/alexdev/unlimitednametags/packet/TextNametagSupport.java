package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.player.User;
import com.google.common.collect.Maps;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Per-viewer text display state and updates for {@link TextPacketNameTag} only.
 * Item/block displays do not use this type.
 */
final class TextNametagSupport {

    private record ViewerTextSnap(byte opacity, boolean seeThrough) {
    }

    private final PacketNameTag host;
    private final Map<UUID, Component> relationalCache = Maps.newConcurrentMap();
    private final Map<UUID, Component> calculatedTextCache = Maps.newConcurrentMap();
    private final Map<UUID, Component> forcedRelationalText = Maps.newConcurrentMap();
    private volatile Component forcedGlobalText;
    private final Map<UUID, ViewerTextSnap> obscuredPresentationByViewer = new ConcurrentHashMap<>();

    TextNametagSupport(@NotNull final PacketNameTag host) {
        this.host = host;
    }

    boolean text(@NotNull final UUID viewerId, @NotNull final Component text) {
        if (host.isRemoved()) {
            return false;
        }

        calculatedTextCache.put(viewerId, text);

        if (forcedRelationalText.containsKey(viewerId) || forcedGlobalText != null) {
            return false;
        }

        return applyForcedOrCachedText(viewerId, text);
    }

    private boolean applyForcedOrCachedText(@NotNull final UUID viewerId, @NotNull final Component text) {
        if (text.equals(relationalCache.get(viewerId))) {
            return false;
        }

        final User user = host.getPlatform().resolveUser(viewerId);
        if (user == null) {
            return false;
        }

        if (!modifyTextForViewer(user, meta -> meta.setText(text))) {
            return false;
        }

        relationalCache.put(viewerId, text);
        host.markViewerNeedsFullRefresh(viewerId);
        host.touchLastUpdate();
        return true;
    }

    boolean modifyTextForViewer(@Nullable final User user, @NotNull final Consumer<TextDisplayMeta> consumer) {
        if (host.isRemoved() || user == null) {
            return false;
        }

        final AtomicBoolean modified = new AtomicBoolean(false);
        host.getPerPlayerEntity().modify(user, e -> {
            if (e == null) {
                return;
            }
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
            modified.set(true);
        });
        return modified.get();
    }

    void modifyTextForOwner(@NotNull final Consumer<TextDisplayMeta> consumer) {
        if (host.isRemoved()) {
            return;
        }

        final User ownerUser = host.getPlatform().resolveUser(host.getOwnerId());
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

    void setBackgroundColor(final int argb) {
        modifyTextAll(meta -> meta.setBackgroundColor(argb));
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

    void onViewerDetached(@NotNull final UUID viewerId) {
        relationalCache.remove(viewerId);
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

        final UUID ownerId = host.getOwnerId();
        final boolean baseSeeThrough = false;

        for (final UUID viewerId : new ArrayList<>(host.getViewers())) {
            if (host.getPlatform().viewerLacksTextDisplaySupport(viewerId)) {
                continue;
            }

            byte opacity = -1;
            boolean seeThroughMeta = baseSeeThrough;

            if (sneakEffective) {
                opacity = sneakOpacityByte;
            } else if (!viewerId.equals(ownerId)) {
                final double distSq = host.getPlatform().distanceSquaredSameWorld(ownerId, viewerId);
                if (distSq >= 0 && distSq <= maxDistanceSq
                        && !host.getPlatform().hasLineOfSight(viewerId, ownerId)) {
                    opacity = obscuredOpacityByte;
                    seeThroughMeta = true;
                }
            }

            final ViewerTextSnap prev = obscuredPresentationByViewer.get(viewerId);
            if (prev != null && prev.opacity() == opacity && prev.seeThrough() == seeThroughMeta) {
                continue;
            }
            obscuredPresentationByViewer.put(viewerId, new ViewerTextSnap(opacity, seeThroughMeta));

            final User user = host.getPlatform().resolveUser(viewerId);
            if (user == null) {
                continue;
            }
            final byte opacityFinal = opacity;
            final boolean seeThroughFinal = seeThroughMeta;
            modifyTextForViewer(user, m -> {
                m.setTextOpacity(opacityFinal);
                m.setSeeThrough(seeThroughFinal);
            });
            host.refreshForViewer(viewerId);
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
        Component textToUse = calculatedTextCache.get(uuid);

        if (forcedRelationalText.containsKey(uuid)) {
            textToUse = forcedRelationalText.get(uuid);
        } else if (forcedGlobalText != null) {
            textToUse = forcedGlobalText;
        }

        if (textToUse != null) {
            if (applyForcedOrCachedText(uuid, textToUse)) {
                host.refreshForViewer(uuid);
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

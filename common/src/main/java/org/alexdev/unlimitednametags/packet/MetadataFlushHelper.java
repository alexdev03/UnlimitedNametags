package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import me.tofaa.entitylib.meta.Metadata;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Flushes metadata changes of per-viewer display entities as delta packets.
 * <p>
 * EntityLib only records pending deltas ({@code notNotifiedChanges}) for entities registered in its global
 * registry. Nametag displays are per-viewer {@link WrapperEntity} instances that are never registered, so
 * {@link Metadata#setIndex} stores the new value without marking it pending. The delta is therefore computed
 * against a snapshot of what was last sent to the client for each {@link Metadata} instance.
 * <p>
 * Coupled to EntityLib 3.x internals; see {@code gradle/libs.versions.toml} entityLibVersion.
 */
final class MetadataFlushHelper {

    /** Last values sent to the client, per metadata instance (index -> value). */
    private static final Map<Metadata, Map<Integer, Object>> LAST_SENT =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final MethodHandle NOT_NOTIFIED_CHANGES;

    static {
        try {
            final Field field = Metadata.class.getDeclaredField("notNotifiedChanges");
            field.setAccessible(true);
            NOT_NOTIFIED_CHANGES = MethodHandles.lookup().unreflectGetter(field);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(
                    "EntityLib Metadata.notNotifiedChanges field missing; check entityLibVersion compatibility: "
                            + e.getMessage());
        }
    }

    private MetadataFlushHelper() {
    }

    static boolean hasPending(@NotNull final Metadata metadata) {
        return !pendingEntries(metadata).isEmpty();
    }

    /**
     * Marks the current metadata as fully sent (after a full refresh/spawn packet).
     */
    static void clearPending(@NotNull final Metadata metadata) {
        synchronized (pendingMap(metadata)) {
            pendingMap(metadata).clear();
        }
        LAST_SENT.put(metadata, snapshot(currentEntries(metadata)));
    }

    /**
     * Returns the entries that differ from the last snapshot sent to the client and records them as sent.
     * Without a snapshot (never flushed) every entry is returned.
     */
    @NotNull
    static List<EntityData<?>> collectChanges(@NotNull final Metadata metadata) {
        final List<EntityData<?>> current = currentEntries(metadata);
        final List<EntityData<?>> changed = new ArrayList<>();
        synchronized (LAST_SENT) {
            final Map<Integer, Object> previous = LAST_SENT.get(metadata);
            for (final EntityData<?> entry : current) {
                if (previous == null || !previous.containsKey(entry.getIndex())
                        || !Objects.equals(previous.get(entry.getIndex()), entry.getValue())) {
                    changed.add(entry);
                }
            }
            LAST_SENT.put(metadata, snapshot(current));
        }
        synchronized (pendingMap(metadata)) {
            pendingMap(metadata).clear();
        }
        return changed;
    }

    @NotNull
    private static List<EntityData<?>> currentEntries(@NotNull final Metadata metadata) {
        return metadata.createPacket().getEntityMetadata();
    }

    @NotNull
    private static Map<Integer, Object> snapshot(@NotNull final List<EntityData<?>> entries) {
        final Map<Integer, Object> values = new HashMap<>(entries.size() * 2);
        for (final EntityData<?> entry : entries) {
            values.put(entry.getIndex(), entry.getValue());
        }
        return values;
    }

    /**
     * Sends a delta metadata packet for pending indices only.
     *
     * @return {@code true} if a packet was sent
     */
    static boolean flushPending(@NotNull final WrapperEntity entity) {
        if (!entity.isSpawned()) {
            return false;
        }

        return flushPending(entity, entity.getEntityMeta().getMetadata());
    }

    static boolean flushPending(@NotNull final WrapperEntity entity, @NotNull final Metadata metadata) {
        if (!entity.isSpawned()) {
            return false;
        }

        final List<EntityData<?>> entries = collectChanges(metadata);
        if (entries.isEmpty()) {
            return false;
        }

        entity.sendPacketToViewers(new WrapperPlayServerEntityMetadata(entity.getEntityId(), entries));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Byte, EntityData<?>> pendingMap(@NotNull final Metadata metadata) {
        try {
            return (Map<Byte, EntityData<?>>) NOT_NOTIFIED_CHANGES.invoke(metadata);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to read Metadata.notNotifiedChanges", t);
        }
    }

    @NotNull
    private static List<EntityData<?>> pendingEntries(@NotNull final Metadata metadata) {
        synchronized (pendingMap(metadata)) {
            return new ArrayList<>(pendingMap(metadata).values());
        }
    }
}

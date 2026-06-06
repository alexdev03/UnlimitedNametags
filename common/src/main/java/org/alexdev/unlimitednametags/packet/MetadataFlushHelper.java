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
import java.util.List;
import java.util.Map;

/**
 * Flushes EntityLib {@link Metadata} pending deltas ({@code notNotifiedChanges}) without toggling
 * {@code setNotifyAboutChanges(true)} (that path requires a globally registered entity id).
 * <p>
 * Coupled to EntityLib 3.0.x internals; see {@code gradle/libs.versions.toml} entityLibVersion.
 */
final class MetadataFlushHelper {

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

    static void clearPending(@NotNull final Metadata metadata) {
        synchronized (pendingMap(metadata)) {
            pendingMap(metadata).clear();
        }
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

        final Metadata metadata = entity.getEntityMeta().getMetadata();
        final List<EntityData<?>> entries;
        synchronized (pendingMap(metadata)) {
            if (pendingMap(metadata).isEmpty()) {
                return false;
            }
            entries = new ArrayList<>(pendingMap(metadata).values());
            pendingMap(metadata).clear();
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

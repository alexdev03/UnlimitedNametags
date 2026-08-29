package org.alexdev.unlimitednametags.packet;

import me.tofaa.entitylib.meta.Metadata;
import org.jetbrains.annotations.NotNull;

/**
 * Copies EntityLib metadata through its concurrent metadata snapshot instead of
 * {@link Metadata#copyFrom(Metadata)}, which iterates an unsynchronized pending-change map.
 */
final class MetadataSnapshotCopier {

    private MetadataSnapshotCopier() {
    }

    static void copy(@NotNull Metadata source, @NotNull Metadata target) {
        target.clear();
        target.setMetaFromPacket(source.createPacket());
    }
}

package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import me.tofaa.entitylib.meta.Metadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataSnapshotCopierTest {

    @BeforeEach
    void setUpPacketEvents() {
        final PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        final ServerManager serverManager = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(serverManager);
        when(serverManager.getVersion()).thenReturn(ServerVersion.V_1_21_10);
        PacketEvents.setAPI(api);
    }

    @AfterEach
    void clearPacketEvents() {
        PacketEvents.setAPI(null);
    }

    @Test
    void replacesTargetWithSourceSnapshot() {
        final Metadata source = new MetadataWithUnsafeCopyPath(1);
        final Metadata target = new Metadata(2);
        source.setMetaFromPacket(packet(1, new EntityData<>((byte) 1, null, (byte) 42)));
        target.setMetaFromPacket(packet(2, new EntityData<>((byte) 2, null, (byte) 7)));

        MetadataSnapshotCopier.copy(source, target);

        assertEquals((byte) 42, target.getIndex((byte) 1, (byte) 0));
        assertEquals((byte) 0, target.getIndex((byte) 2, (byte) 0));
    }

    private static WrapperPlayServerEntityMetadata packet(int entityId, EntityData<?> data) {
        return new WrapperPlayServerEntityMetadata(entityId, List.of(data));
    }

    private static final class MetadataWithUnsafeCopyPath extends Metadata {
        private MetadataWithUnsafeCopyPath(int entityId) {
            super(entityId);
        }

        @Override
        public void copyTo(Metadata other) {
            throw new ConcurrentModificationException("EntityLib copyTo iterates mutable pending metadata");
        }
    }
}

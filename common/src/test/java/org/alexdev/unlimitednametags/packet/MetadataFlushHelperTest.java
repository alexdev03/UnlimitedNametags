package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import me.tofaa.entitylib.meta.Metadata;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nametag displays are per-viewer entities that EntityLib never registers globally, so
 * {@link Metadata#setIndex} does not record pending changes for them. The flush must still send them.
 */
class MetadataFlushHelperTest {

    private static final byte SCALE_INDEX = 12;
    private static final byte TRANSLATION_INDEX = 11;

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
    void flushPendingSendsScaleChangeOfUnregisteredSpawnedEntity() {
        final Metadata metadata = new Metadata(99);
        set(metadata, SCALE_INDEX, new Vector3f(1f, 1f, 1f));
        final WrapperEntity entity = spawnedEntity(99);
        MetadataFlushHelper.clearPending(metadata);
        assertTrue(!MetadataFlushHelper.flushPending(entity, metadata));
        verify(entity, never()).sendPacketToViewers(any());

        set(metadata, SCALE_INDEX, new Vector3f(4f, 4f, 4f));

        assertTrue(MetadataFlushHelper.flushPending(entity, metadata));
        final ArgumentCaptor<WrapperPlayServerEntityMetadata> sent = ArgumentCaptor.forClass(WrapperPlayServerEntityMetadata.class);
        verify(entity).sendPacketToViewers(sent.capture());
        assertEquals(1, sent.getValue().getEntityMetadata().size());
        assertEquals(new Vector3f(4f, 4f, 4f), sent.getValue().getEntityMetadata().get(0).getValue());
    }

    /** Same storage path as {@code Metadata#setIndex}, without loading packetevents type mappings. */
    private static void set(Metadata metadata, byte index, Object value) {
        metadata.setMetaFromPacket(new WrapperPlayServerEntityMetadata(0, List.of(new EntityData<>(index, null, value))));
    }

    private static WrapperEntity spawnedEntity(int id) {
        final WrapperEntity entity = mock(WrapperEntity.class);
        when(entity.isSpawned()).thenReturn(true);
        when(entity.getEntityId()).thenReturn(id);
        return entity;
    }

    @Test
    void sendsChangesOfUnregisteredEntity() {
        final Metadata metadata = new Metadata(100);
        set(metadata, SCALE_INDEX, new Vector3f(1f, 1f, 1f));
        set(metadata, TRANSLATION_INDEX, new Vector3f(0f, 0.3f, 0f));
        MetadataFlushHelper.clearPending(metadata); // full spawn/refresh sent

        set(metadata, SCALE_INDEX, new Vector3f(3f, 3f, 3f));

        final List<EntityData<?>> changes = MetadataFlushHelper.collectChanges(metadata);
        assertEquals(1, changes.size());
        assertEquals(SCALE_INDEX, changes.get(0).getIndex());
        assertEquals(new Vector3f(3f, 3f, 3f), changes.get(0).getValue());
    }

    @Test
    void unchangedValuesAreNotResent() {
        final Metadata metadata = new Metadata(101);
        set(metadata, SCALE_INDEX, new Vector3f(2f, 2f, 2f));
        MetadataFlushHelper.clearPending(metadata);

        set(metadata, SCALE_INDEX, new Vector3f(2f, 2f, 2f));

        assertTrue(MetadataFlushHelper.collectChanges(metadata).isEmpty());
    }

    @Test
    void changesAreSentOnlyOnce() {
        final Metadata metadata = new Metadata(102);
        set(metadata, TRANSLATION_INDEX, new Vector3f(0f, 0.3f, 0f));
        MetadataFlushHelper.clearPending(metadata);

        set(metadata, TRANSLATION_INDEX, new Vector3f(0f, 0.9f, 0f));

        assertEquals(1, MetadataFlushHelper.collectChanges(metadata).size());
        assertTrue(MetadataFlushHelper.collectChanges(metadata).isEmpty());
    }

    @Test
    void neverFlushedMetadataSendsEverything() {
        final Metadata metadata = new Metadata(103);
        set(metadata, SCALE_INDEX, new Vector3f(1f, 1f, 1f));
        set(metadata, TRANSLATION_INDEX, new Vector3f(0f, 0.3f, 0f));

        assertEquals(2, MetadataFlushHelper.collectChanges(metadata).size());
    }
}

package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import org.alexdev.unlimitednametags.TestInstances;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.listeners.TrackerManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PacketManagerPoseTest {
    private PacketManager manager;
    private final Player owner = mock(Player.class);
    private final Player tracked = mock(Player.class);
    private final User viewer = mock(User.class);
    private final PassengerState state = new PassengerState();
    private final Map<User, PassengerState> connections = new ConcurrentHashMap<>();
    private final List<Runnable> queued = new ArrayList<>();
    private MockedStatic<ChannelHelper> channels;

    @BeforeEach void setup() throws Exception {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        PlayerManager players = mock(PlayerManager.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getPlayerManager()).thenReturn(players);
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_10);
        when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
        PacketEvents.setAPI(api);
        when(players.getUser(tracked)).thenReturn(viewer);
        when(viewer.getChannel()).thenReturn(new Object());
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getUUID()).thenReturn(UUID.randomUUID());
        when(owner.getEntityId()).thenReturn(7);
        UnlimitedNameTags plugin = TestInstances.allocate(UnlimitedNameTags.class);
        TrackerManager tracker = mock(TrackerManager.class);
        when(tracker.getWhoTracks(owner)).thenReturn(List.of(tracked));
        TestInstances.set(plugin, "trackerManager", tracker);
        PacketManager allocated = TestInstances.allocate(PacketManager.class);
        TestInstances.set(allocated, "plugin", plugin);
        TestInstances.set(allocated, "connections", connections);
        manager = spy(allocated);
        doReturn(true).when(manager).knowsOwner(viewer, owner);
        doReturn(true).when(manager).isCurrent(viewer);
        state.spawn(7);
        connections.put(viewer, state);
        channels = mockStatic(ChannelHelper.class);
        channels.when(() -> ChannelHelper.runInEventLoop(any(), any())).thenAnswer(call -> {
            queued.add(call.getArgument(1));
            return null;
        });
    }

    @AfterEach void close() {
        channels.close();
        PacketEvents.setAPI(null);
    }

    private void enqueue() { manager.sendPoseSnapshot(owner, EntityPose.STANDING, () -> true); }
    private void flush() { queued.forEach(Runnable::run); }

    @Test void sendsOnlyPoseToKnownOwnerWithoutChangingOtherMetadata() {
        enqueue();
        verify(viewer, never()).sendPacket(any());
        flush();
        ArgumentCaptor<WrapperPlayServerEntityMetadata> packet = ArgumentCaptor.forClass(WrapperPlayServerEntityMetadata.class);
        verify(viewer).sendPacket(packet.capture());
        assertEquals(7, packet.getValue().getEntityId());
        var data = packet.getValue().getEntityMetadata();
        assertEquals(1, data.size());
        assertEquals(6, data.get(0).getIndex());
        assertEquals(EntityDataTypes.ENTITY_POSE, data.get(0).getType());
        assertEquals(EntityPose.STANDING, data.get(0).getValue());
        verify(owner, never()).setPose(any());
    }

    @Test void unknownOwnerDoesNotQueue() {
        doReturn(false).when(manager).knowsOwner(viewer, owner);
        enqueue();
        assertTrue(queued.isEmpty());
    }

    @Test void destroyBeforeFlushRejectsPose() {
        enqueue();
        state.destroy(7);
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void destroyAndRespawnOfSameEntityIdRejectsOldPoseSnapshot() {
        enqueue();
        state.destroy(7);
        state.spawn(7);
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void worldResetBeforeFlushRejectsOldState() {
        enqueue();
        connections.put(viewer, new PassengerState());
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void disconnectedViewerBeforeFlushIsRejected() {
        enqueue();
        doReturn(false).when(manager).isCurrent(viewer);
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void trackingVetoBeforeFlushIsRejected() {
        enqueue();
        doReturn(false).when(manager).knowsOwner(viewer, owner);
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void newerPoseBeforeFlushRejectsStaleSnapshot() {
        AtomicBoolean valid = new AtomicBoolean(true);
        manager.sendPoseSnapshot(owner, EntityPose.SWIMMING, valid::get);
        valid.set(false);
        flush();
        verify(viewer, never()).sendPacket(any());
    }

    @Test void ownersOwnPredictedPoseIsNotOverwritten() {
        UUID ownerId = owner.getUniqueId();
        when(viewer.getUUID()).thenReturn(ownerId);
        enqueue();
        assertTrue(queued.isEmpty());
    }
}

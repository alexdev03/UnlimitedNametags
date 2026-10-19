package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.player.User;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PacketNameTagLifecycleTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final NametagRuntime runtime = mock(NametagRuntime.class);
    private final NametagPlatformBridge platform = mock(NametagPlatformBridge.class);
    private final PacketNameTag row = new PacketNameTag(runtime, platform, mock(NametagMaterialBridge.class),
            owner, Settings.DisplayGroup.builder().build()) {
        protected Function<User, WrapperEntity> buildBaseSupplier() { return user -> mock(WrapperEntity.class); }
        protected void applyViewerOwnerMetadata(WrapperEntity a, WrapperEntity b) {}
        protected void appendTypeProperties(Map<String, String> properties, AbstractDisplayMeta meta) {}
    };

    private WrapperEntity allocated(boolean spawned, boolean viewing) {
        WrapperEntity wrapper = mock(WrapperEntity.class);
        when(wrapper.isSpawned()).thenReturn(spawned);
        when(wrapper.getViewers()).thenReturn(viewing ? Set.of(viewer) : Set.of());
        row.getPerPlayerEntity().getEntities().put(viewer, wrapper);
        return wrapper;
    }

    @Test void allocatedRowRegistersOwnerForRemovalCleanup() {
        verify(runtime).registerRow(owner, row.getEntityId());
    }

    @Test void metadataOnlyWrapperIsNotVisible() {
        allocated(false, false);
        assertFalse(row.canViewerSee(viewer));
        assertTrue(row.getViewers().isEmpty());
    }

    @Test void spawnedWithoutViewerIsNotVisible() {
        allocated(true, false);
        assertFalse(row.canViewerSee(viewer));
    }

    @Test void spawnedViewerRemainsVisible() {
        allocated(true, true);
        assertTrue(row.canViewerSee(viewer));
    }

    @Test void quitReleasesWrapperBeforeForgettingItEvenIfUserIsUnavailable() {
        WrapperEntity wrapper = allocated(true, true);
        row.handleQuit(viewer);
        verify(wrapper).removeViewer(viewer);
        verify(wrapper).remove();
        assertTrue(row.getPerPlayerEntity().getEntities().isEmpty());
    }

    @Test void blockedViewerStillGetsDestroyedOnHide() {
        WrapperEntity wrapper = allocated(true, true);
        row.getBlocked().add(viewer);
        row.hideFromViewer(viewer);
        verify(wrapper).removeViewer(viewer);
        assertTrue(row.getPerPlayerEntity().getEntities().isEmpty());
    }

    @Test void delayedMountCannotRunForDetachedViewer() {
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(viewer);
        row.sendPassengersPacket(user);
        verify(runtime, never()).sendPassengersPacket(any(), any());
    }

    @Test void removedRowCannotAllocateOrShowAgain() {
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(viewer);
        when(platform.resolveUser(viewer)).thenReturn(user);
        when(platform.isEligibleToShow(any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);
        row.remove();
        row.showToViewer(viewer);
        assertTrue(row.getPerPlayerEntity().getEntities().isEmpty());
        verify(runtime, never()).runTaskLaterAsync(any(), anyLong());
    }

    @Test void directOwnerSpawnRespectsOwnerEligibilityAndKeepsOtherViewer() {
        WrapperEntity other = allocated(true, true);
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(owner);
        when(platform.resolveUser(owner)).thenReturn(user);
        when(platform.offsetDisplayLocation(anyFloat())).thenReturn(
                new com.github.retrooper.packetevents.protocol.world.Location(0, 64, 0, 0, 0));
        when(platform.isEligibleToShow(eq(owner), eq(owner), anyBoolean(), anyBoolean())).thenReturn(false);
        row.spawnForOwner();
        WrapperEntity template = row.getPerPlayerEntity().getEntities().get(owner);
        assertNotNull(template);
        verify(template, never()).spawn(any(com.github.retrooper.packetevents.protocol.world.Location.class));
        verify(template, never()).addViewer(any(User.class));
        assertTrue(row.canViewerSee(viewer));
        verify(other, never()).removeViewer(any(UUID.class));
    }

    @Test void rawOwnerViewerSpawnCannotBypassMovementOrPreferenceGate() {
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(owner);
        when(platform.resolveUser(owner)).thenReturn(user);
        when(platform.offsetDisplayLocation(anyFloat())).thenReturn(
                new com.github.retrooper.packetevents.protocol.world.Location(0, 64, 0, 0, 0));
        row.setVisible(true);
        row.spawnViewer(owner);
        WrapperEntity template = row.getPerPlayerEntity().getEntities().get(owner);
        assertNotNull(template);
        verify(template, never()).spawn(any(com.github.retrooper.packetevents.protocol.world.Location.class));
        verify(template, never()).addViewer(any(User.class));
    }
    @Test void visibleCurrentSessionCanRequestMount() {
        allocated(true, true);
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(viewer);
        when(platform.resolveUser(viewer)).thenReturn(user);
        row.sendPassengersPacket(user);
        verify(runtime).sendPassengersPacket(user, owner);
    }

    @Test void oldConnectionCannotMountRowsInNewSession() {
        allocated(true, true);
        User oldUser = mock(User.class);
        when(oldUser.getUUID()).thenReturn(viewer);
        when(platform.resolveUser(viewer)).thenReturn(mock(User.class));
        row.sendPassengersPacket(oldUser);
        verify(runtime, never()).sendPassengersPacket(any(), any());
    }

    @Test void removedRowRejectsMetadataAllocation() {
        User user = mock(User.class);
        when(user.getUUID()).thenReturn(viewer);
        row.remove();
        row.modifyEntity(user, entity -> fail("Removed generation was modified"));
        assertTrue(row.getPerPlayerEntity().getEntities().isEmpty());
    }

}

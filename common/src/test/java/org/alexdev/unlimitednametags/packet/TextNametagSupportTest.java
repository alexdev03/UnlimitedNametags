package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import me.tofaa.entitylib.wrapper.WrapperPerPlayerEntity;
import org.alexdev.unlimitednametags.platform.NametagPlatformBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextNametagSupportTest {
    @BeforeAll static void initializeMetadataProtocol() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_10);
        PacketEvents.setAPI(api);
    }
    @AfterAll static void clearProtocol() { PacketEvents.setAPI(null); }
    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final PacketNameTag host = mock(PacketNameTag.class);
    private final NametagPlatformBridge platform = mock(NametagPlatformBridge.class);
    private final WrapperPerPlayerEntity displays = mock(WrapperPerPlayerEntity.class);
    private final WrapperEntity wrapper = mock(WrapperEntity.class);
    private final TextDisplayMeta metadata = mock(TextDisplayMeta.class);
    private final User user = mock(User.class);
    private final AtomicInteger opacity = new AtomicInteger(-1);
    private final AtomicBoolean seeThrough = new AtomicBoolean();
    private final TextNametagSupport support = new TextNametagSupport(host);

    @BeforeEach void setup() {
        when(host.getOwnerId()).thenReturn(owner);
        when(host.getViewers()).thenReturn(Set.of(viewer));
        when(host.getPlatform()).thenReturn(platform);
        when(host.getPerPlayerEntity()).thenReturn(displays);
        when(displays.getEntities()).thenReturn(new HashMap<>(Map.of(viewer, wrapper)));
        when(wrapper.getEntityMeta()).thenReturn(metadata);
        when(platform.resolveUser(viewer)).thenReturn(user);
        when(platform.distanceSquaredSameWorld(owner, viewer)).thenReturn(4.0);
        when(metadata.getTextOpacity()).thenAnswer(invocation -> (byte) opacity.get());
        when(metadata.isSeeThrough()).thenAnswer(invocation -> seeThrough.get());
        doAnswer(invocation -> {
            opacity.set(invocation.getArgument(0, Byte.class));
            return null;
        })
                .when(metadata).setTextOpacity(anyByte());
        doAnswer(invocation -> {
            seeThrough.set(invocation.getArgument(0));
            return null;
        })
                .when(metadata).setSeeThrough(anyBoolean());
        doAnswer(invocation -> {
            invocation.<Consumer<WrapperEntity>>getArgument(1).accept(wrapper);
            return null;
        }).when(displays).modify(eq(user), any());
    }

    private void apply() { support.applyObscuredLineOfSightPresentation(true, (byte) 70, (byte) 80, 64.0, false); }

    private void applyNormalDepth(boolean allowed) throws Exception {
        var method = TextNametagSupport.class.getDeclaredMethod("applySeeThroughLineOfSightPresentation", boolean.class);
        method.setAccessible(true);
        method.invoke(support, allowed);
    }

    @Test void clearSightUsesNormalDepthWithoutChangingOpacity() throws Exception {
        seeThrough.set(true);
        opacity.set(70);
        when(platform.hasLineOfSight(viewer, owner)).thenReturn(true);
        applyNormalDepth(true);
        assertFalse(seeThrough.get());
        assertEquals(70, opacity.get());
        verify(host).refreshForViewer(viewer);
    }

    @Test void blockedSightRetainsConfiguredThroughWallRendering() throws Exception {
        applyNormalDepth(true);
        assertTrue(seeThrough.get());
        applyNormalDepth(false);
        assertFalse(seeThrough.get());
    }

    @Test void normalDepthRepairSurvivesAStyleResetWithoutRedundantWrites() throws Exception {
        when(platform.hasLineOfSight(viewer, owner)).thenReturn(true);
        applyNormalDepth(true);
        verify(host, never()).refreshForViewer(viewer);
        seeThrough.set(true);
        applyNormalDepth(true);
        applyNormalDepth(true);
        assertFalse(seeThrough.get());
        verify(host).refreshForViewer(viewer);
    }

    @Test void crossWorldAndUnsupportedViewersAreNotModified() throws Exception {
        when(platform.distanceSquaredSameWorld(owner, viewer)).thenReturn(-1.0);
        applyNormalDepth(true);
        verify(host, never()).refreshForViewer(viewer);
        when(platform.distanceSquaredSameWorld(owner, viewer)).thenReturn(4.0);
        when(platform.viewerLacksTextDisplaySupport(viewer)).thenReturn(true);
        applyNormalDepth(true);
        verify(host, never()).refreshForViewer(viewer);
    }

    @Test void missingConnectionCanRetryNormalDepthPresentation() throws Exception {
        when(platform.resolveUser(viewer)).thenReturn(null);
        applyNormalDepth(true);
        assertFalse(seeThrough.get());
        when(platform.resolveUser(viewer)).thenReturn(user);
        applyNormalDepth(true);
        assertTrue(seeThrough.get());
    }

    @Test void clearAndBlockedViewersKeepIndependentNormalDepthStates() throws Exception {
        UUID second = UUID.randomUUID();
        User secondUser = mock(User.class);
        WrapperEntity secondWrapper = mock(WrapperEntity.class);
        TextDisplayMeta secondMeta = mock(TextDisplayMeta.class);
        when(secondWrapper.getEntityMeta()).thenReturn(secondMeta);
        displays.getEntities().put(second, secondWrapper);
        when(host.getViewers()).thenReturn(Set.of(viewer, second));
        when(platform.resolveUser(second)).thenReturn(secondUser);
        when(platform.distanceSquaredSameWorld(owner, second)).thenReturn(4.0);
        when(platform.hasLineOfSight(second, owner)).thenReturn(true);
        when(secondMeta.isSeeThrough()).thenReturn(true);
        doAnswer(call -> {
            call.<Consumer<WrapperEntity>>getArgument(1).accept(secondWrapper);
            return null;
        })
                .when(displays).modify(eq(secondUser), any());
        applyNormalDepth(true);
        assertTrue(seeThrough.get());
        verify(secondMeta).setSeeThrough(false);
        verify(secondMeta, never()).setTextOpacity(anyByte());
    }

    @Test void ownNametagUsesNormalDepthEvenWithoutAnOwnerRay() throws Exception {
        when(host.getOwnerId()).thenReturn(viewer);
        seeThrough.set(true);
        applyNormalDepth(true);
        assertFalse(seeThrough.get());
        verify(platform, never()).hasLineOfSight(any(), any());
    }

    @Test void repeatedStateAvoidsRedundantWrites() {
        apply();
        apply();
        assertEquals(80, opacity.get());
        assertTrue(seeThrough.get());
        verify(host, times(1)).refreshForViewer(viewer);
    }

    @Test void refreshThatResetsWallFlagIsRepairedDespiteCachedState() {
        apply();
        seeThrough.set(false);
        apply();
        assertTrue(seeThrough.get());
        verify(host, times(2)).refreshForViewer(viewer);
    }

    @Test void replacementMetadataOpacityIsRepairedDespiteCachedState() {
        apply();
        opacity.set(-1);
        apply();
        assertEquals(80, opacity.get());
        verify(host, times(2)).refreshForViewer(viewer);
    }

    @Test void unavailableUserDoesNotCacheAnUnsentPresentation() {
        when(platform.resolveUser(viewer)).thenReturn(null);
        apply();
        when(platform.resolveUser(viewer)).thenReturn(user);
        apply();
        assertEquals(80, opacity.get());
        assertTrue(seeThrough.get());
        verify(host).refreshForViewer(viewer);
    }

    @Test void failedModificationCanRetry() {
        doNothing().when(displays).modify(eq(user), any());
        apply();
        doAnswer(invocation -> {
            invocation.<Consumer<WrapperEntity>>getArgument(1).accept(wrapper);
            return null;
        }).when(displays).modify(eq(user), any());
        apply();
        assertEquals(80, opacity.get());
        verify(host).refreshForViewer(viewer);
    }

    @Test void lineOfSightRestoresFullOpacityAndNormalDepth() {
        apply();
        when(platform.hasLineOfSight(viewer, owner)).thenReturn(true);
        apply();
        assertEquals(-1, opacity.get());
        assertFalse(seeThrough.get());
    }

    @Test void viewersKeepIndependentPresentation() {
        UUID second = UUID.randomUUID();
        User secondUser = mock(User.class);
        WrapperEntity secondWrapper = mock(WrapperEntity.class);
        TextDisplayMeta secondMetadata = mock(TextDisplayMeta.class);
        when(secondWrapper.getEntityMeta()).thenReturn(secondMetadata);
        displays.getEntities().put(second, secondWrapper);
        when(host.getViewers()).thenReturn(Set.of(viewer, second));
        when(platform.resolveUser(second)).thenReturn(secondUser);
        when(platform.distanceSquaredSameWorld(owner, second)).thenReturn(4.0);
        when(platform.hasLineOfSight(second, owner)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Consumer<WrapperEntity>>getArgument(1).accept(secondWrapper);
            return null;
        }).when(displays).modify(eq(secondUser), any());
        apply();
        assertTrue(seeThrough.get());
        verify(secondMetadata).setTextOpacity((byte) -1);
        verify(secondMetadata).setSeeThrough(false);
    }
}

package org.alexdev.unlimitednametags.nametags;

import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import org.alexdev.unlimitednametags.TestInstances;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PaperNametagRow;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.mockito.Mockito.*;

class NameTagManagerVisibilityTest {
    @BeforeAll static void initializeMetadataProtocol() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_10);
        PacketEvents.setAPI(api);
    }
    @AfterAll static void clearProtocol() { PacketEvents.setAPI(null); }
    private NameTagManager manager;
    private final Settings settings = new Settings();
    @BeforeEach void setup() throws Exception {
        manager = TestInstances.allocate(NameTagManager.class);
        UnlimitedNameTags plugin = TestInstances.allocate(UnlimitedNameTags.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getSettings()).thenReturn(settings);
        TestInstances.set(plugin, "configManager", config);
        TestInstances.set(manager, "plugin", plugin);
    }

    private void refreshStyle(Settings.ThroughWallMode mode, boolean force) throws Exception {
        TestInstances.set(settings.getVisibility(), "throughWallMode", mode);
        PacketNameTag row = mock(PacketNameTag.class);
        TextDisplayMeta meta = mock(TextDisplayMeta.class);
        when(meta.isSeeThrough()).thenReturn(true);
        Method method = NameTagManager.class.getDeclaredMethod("applyTextVisualState",
                PacketNameTag.class, Settings.DisplayGroup.class, TextDisplayMeta.class, boolean.class);
        method.setAccessible(true);
        method.invoke(manager, row, Settings.DisplayGroup.builder().build(), meta, force);
        if (mode == Settings.ThroughWallMode.OBSCURED) {
            verify(meta, never()).setSeeThrough(anyBoolean());
        } else {
            verify(meta).setSeeThrough(false);
        }
    }

    @Test void obscuredPlaceholderRefreshPreservesViewerWallFlag() throws Exception {
        refreshStyle(Settings.ThroughWallMode.OBSCURED, false);
    }
    @Test void forcedObscuredStyleRefreshPreservesViewerWallFlag() throws Exception {
        refreshStyle(Settings.ThroughWallMode.OBSCURED, true);
    }
    @Test void hideModeStillAppliesNormalDepthDuringRefresh() throws Exception {
        refreshStyle(Settings.ThroughWallMode.HIDE, true);
    }

    @Test void waterMitigationIsOptInAndStyleRefreshPreservesPerViewerFlags() throws Exception {
        org.junit.jupiter.api.Assertions.assertFalse(settings.getVisibility().isPreferNormalTextWithLineOfSight());
        TestInstances.set(settings.getVisibility(), "preferNormalTextWithLineOfSight", true);
        PacketNameTag row = mock(PacketNameTag.class);
        TextDisplayMeta meta = mock(TextDisplayMeta.class);
        Method method = NameTagManager.class.getDeclaredMethod("applyTextVisualState",
                PacketNameTag.class, Settings.DisplayGroup.class, TextDisplayMeta.class, boolean.class);
        method.setAccessible(true);
        method.invoke(manager, row, Settings.DisplayGroup.builder().build(), meta, true);
        verify(meta, never()).setSeeThrough(anyBoolean());
        verify(meta, never()).setTextOpacity(anyByte());
    }

    @Test void normalDepthPresentationRunsOnViewersSchedulerAndRejectsReconnect() throws Exception {
        TestInstances.set(settings.getVisibility(), "preferNormalTextWithLineOfSight", true);
        var scheduler = mock(com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler.class);
        var listener = mock(org.alexdev.unlimitednametags.listeners.PlayerListener.class);
        var pluginField = NameTagManager.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        var plugin = (UnlimitedNameTags) pluginField.get(manager);
        TestInstances.set(plugin, "taskScheduler", scheduler);
        TestInstances.set(plugin, "playerListener", listener);
        Player viewer = mock(Player.class);
        UUID viewerId = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.isOnline()).thenReturn(true);
        when(listener.getPlayer(viewerId)).thenReturn(viewer);
        PacketNameTag row = mock(PacketNameTag.class);
        when(row.isTextDisplay()).thenReturn(true);
        when(row.getViewers()).thenReturn(java.util.Set.of(viewerId));
        when(row.getDisplayGroup()).thenReturn(Settings.DisplayGroup.builder().build());
        var method = NameTagManager.class.getDeclaredMethod("applyNormalDepthPresentationForViewer", PacketNameTag.class, Player.class);
        method.setAccessible(true);
        method.invoke(manager, row, viewer);
        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(viewer), task.capture());
        verify(row, never()).applySeeThroughLineOfSightPresentationForViewer(any(), anyBoolean());
        task.getValue().run();
        verify(row).applySeeThroughLineOfSightPresentationForViewer(eq(viewerId), anyBoolean());
        when(listener.getPlayer(viewerId)).thenReturn(mock(Player.class));
        task.getValue().run();
        verify(row, times(1)).applySeeThroughLineOfSightPresentationForViewer(eq(viewerId), anyBoolean());
    }
    @Test void recordingHidesAllocatedOtherRowsEvenWithNoOwnerOrVisibleWrapper() throws Exception {
        UUID viewerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(viewerId);
        PacketNameTag own = mock(PacketNameTag.class, withSettings().extraInterfaces(PaperNametagRow.class));
        PacketNameTag other = mock(PacketNameTag.class, withSettings().extraInterfaces(PaperNametagRow.class));
        when(own.getOwnerId()).thenReturn(viewerId);
        when(other.getOwnerId()).thenReturn(otherId);
        ConcurrentHashMap<UUID, CopyOnWriteArrayList<PacketNameTag>> rows = new ConcurrentHashMap<>();
        rows.put(viewerId, new CopyOnWriteArrayList<>(java.util.List.of(own)));
        rows.put(otherId, new CopyOnWriteArrayList<>(java.util.List.of(other)));
        TestInstances.set(manager, "nameTags", rows);
        manager.hideAllOthersNametagsFromViewer(viewer);
        verify(other).hideFromViewer(viewerId);
        verify(own, never()).hideFromViewer(any());
        verify(viewer, times(3)).getUniqueId();
        verifyNoMoreInteractions(viewer);
    }
}

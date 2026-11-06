package org.alexdev.unlimitednametags.nametags;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PaperTextPacketNameTag;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NameTagManagerCommandHideTest {

    @Test
    void unitHideSurvivesTheRecoveryThatRunsAfterTeleports() throws Exception {
        final Fixture f = new Fixture();

        f.manager.hideByCommand(f.owner);
        verify(f.row).setVisible(false);
        verify(f.row).clearViewers();

        // Teleport/respawn/gamemode recovery path (PlayerListener#recoverNametagVisibility).
        f.manager.showToTrackedPlayers(f.owner, List.of(f.viewer));

        verify(f.row, never()).setVisible(true);
        verify(f.row, never()).showToPlayers(any());
        assertTrue(f.manager.isHiddenByCommand(f.owner));
    }

    @Test
    void unitShowLiftsTheHide() throws Exception {
        final Fixture f = new Fixture();
        f.manager.hideByCommand(f.owner);

        // The real show path needs a live Bukkit registry (potion check), so only the hand-off is asserted here.
        doNothing().when(f.manager).showToTrackedPlayers(f.owner);

        f.manager.showByCommand(f.owner);

        assertFalse(f.manager.isHiddenByCommand(f.owner));
        verify(f.manager).showToTrackedPlayers(f.owner);
    }

    @Test
    void quitForgetsTheHide() throws Exception {
        final Fixture f = new Fixture();
        f.manager.hideByCommand(f.owner);

        f.manager.clearCache(f.owner.getUniqueId());

        assertFalse(f.manager.isHiddenByCommand(f.owner));
    }

    private static final class Fixture {
        final NameTagManager manager;
        final Player owner = mock(Player.class);
        final Player viewer = mock(Player.class);
        final PaperTextPacketNameTag row = mock(PaperTextPacketNameTag.class);

        Fixture() throws Exception {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            final NameTagManager allocated = (NameTagManager) ((Unsafe) unsafeField.get(null))
                    .allocateInstance(NameTagManager.class);
            final UnlimitedNameTags plugin = mock(UnlimitedNameTags.class, RETURNS_DEEP_STUBS);
            when(plugin.getConfigManager().getSettings().getWorlds().isEnabled(anyString())).thenReturn(true);
            when(plugin.getTrackerManager().getWhoTracks(any(Player.class))).thenReturn(List.of(viewer));

            final World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            final UUID ownerId = UUID.randomUUID();
            when(owner.getUniqueId()).thenReturn(ownerId);
            when(owner.isOnline()).thenReturn(true);
            when(owner.getWorld()).thenReturn(world);
            when(owner.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
            when(viewer.isOnline()).thenReturn(true);
            when(row.getOwner()).thenReturn(owner);

            final ConcurrentHashMap<UUID, CopyOnWriteArrayList<PacketNameTag>> tags = new ConcurrentHashMap<>();
            tags.put(ownerId, new CopyOnWriteArrayList<>(List.of(row)));
            set(allocated, "plugin", plugin);
            set(allocated, "nameTags", tags);
            set(allocated, "blocked", Sets.newConcurrentHashSet());
            set(allocated, "commandHidden", Sets.newConcurrentHashSet());
            for (String name : List.of("creating", "hideNametags", "hideOwnFromSelf", "hideOwnFromOthers",
                    "distanceRefreshCullingBypassed")) {
                set(allocated, name, Sets.newConcurrentHashSet());
            }
            for (String name : List.of("nameTagOverrides", "sessionGlowOverrides", "persistentGlowOverrides",
                    "sessionDisplayAnimations", "persistentDisplayAnimations", "lastDistanceRefreshTicks",
                    "shiftSystemBlocked")) {
                set(allocated, name, new ConcurrentHashMap<>());
            }
            manager = spy(allocated);
            doNothing().when(manager).addPlayer(any(Player.class), anyBoolean());
        }

        private static void set(NameTagManager manager, String name, Object value) throws Exception {
            final Field field = NameTagManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(manager, value);
        }
    }
}

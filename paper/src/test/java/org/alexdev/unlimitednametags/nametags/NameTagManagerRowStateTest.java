package org.alexdev.unlimitednametags.nametags;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PaperTextPacketNameTag;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for two join-time defects seen when a plugin briefly blocks a player (e.g. a join
 * invisibility pulse): rows created outside {@code addPlayer} never received the global Y offset / view
 * range, and the owner was shown their own rows even though {@code showCurrentNameTag} is off.
 */
class NameTagManagerRowStateTest {

    private static final float Y_OFFSET = 0.25f;
    private static final float VIEW_DISTANCE = 60f;

    @Test
    void rowsCreatedOutsideAddPlayerGetTheGlobalOffsetAndViewRange() throws Exception {
        final Fixture f = new Fixture();

        f.manager.initRowBase(f.owner, Settings.DisplayGroup.builder().build(), f.row);

        verify(f.row).resetOffset(Y_OFFSET);
        verify(f.row).setViewRange(VIEW_DISTANCE);
    }

    @Test
    void publicShowDoesNotIncludeTheOwnerWhenOwnNametagIsDisabled() throws Exception {
        final Fixture f = new Fixture();
        // showCurrentNameTag=false and no per-player override => not effective
        doNothing().when(f.manager).showToTrackedPlayers(any(Player.class), anyCollection(), anyBoolean(), anyBoolean());

        f.manager.showToTrackedPlayers(f.owner, List.of(f.viewer));

        verify(f.manager).showToTrackedPlayers(f.owner, List.of(f.viewer), true, false);
    }

    @Test
    void publicShowIncludesTheOwnerWhenOwnNametagIsEnabled() throws Exception {
        final Fixture f = new Fixture();
        when(f.plugin.getConfigManager().getSettings().getVisibility().isShowCurrentNameTag()).thenReturn(true);
        doNothing().when(f.manager).showToTrackedPlayers(any(Player.class), anyCollection(), anyBoolean(), anyBoolean());

        f.manager.showToTrackedPlayers(f.owner, List.of(f.viewer));

        verify(f.manager).showToTrackedPlayers(f.owner, List.of(f.viewer), true, true);
    }

    private static final class Fixture {
        final NameTagManager manager;
        final UnlimitedNameTags plugin = mock(UnlimitedNameTags.class, RETURNS_DEEP_STUBS);
        final Player owner = mock(Player.class);
        final Player viewer = mock(Player.class);
        final PaperTextPacketNameTag row = mock(PaperTextPacketNameTag.class);

        Fixture() throws Exception {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            final NameTagManager allocated = (NameTagManager) ((Unsafe) unsafeField.get(null))
                    .allocateInstance(NameTagManager.class);
            final Settings.Behavior behavior = plugin.getConfigManager().getSettings().getBehavior();
            when(behavior.getYOffset()).thenReturn(Y_OFFSET);
            when(behavior.getViewDistance()).thenReturn(VIEW_DISTANCE);
            when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
            when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());

            set(allocated, "plugin", plugin);
            set(allocated, "nameTags", new ConcurrentHashMap<>());
            set(allocated, "hideOwnFromSelf", Sets.newConcurrentHashSet());
            manager = spy(allocated);
        }

        private static void set(NameTagManager manager, String name, Object value) throws Exception {
            final Field field = NameTagManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(manager, value);
        }
    }
}

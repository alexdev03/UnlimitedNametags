package org.alexdev.unlimitednametags.listeners;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlayerListenerGameModeTest {

    @Test
    void leavingSpectatorRecoversAfterTheNewGameModeIsCommitted() throws Exception {
        UnlimitedNameTags plugin = mock(UnlimitedNameTags.class);
        NameTagManager nameTags = mock(NameTagManager.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        AtomicReference<GameMode> currentMode = new AtomicReference<>(GameMode.SPECTATOR);

        when(plugin.getNametagManager()).thenReturn(nameTags);
        when(plugin.getTaskScheduler()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getGameMode()).thenAnswer(ignored -> currentMode.get());
        when(player.isOnline()).thenReturn(true);

        PlayerListener listener = allocateListener(plugin);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.CREATIVE));

        verify(nameTags, never()).showToTrackedPlayers(player);
        ArgumentCaptor<Runnable> recovery = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), recovery.capture(), eq(1L));

        currentMode.set(GameMode.CREATIVE);
        recovery.getValue().run();

        verify(nameTags).unblockPlayer(player);
        verify(nameTags).showToTrackedPlayers(player);
    }

    @Test
    void staleRecoveryCannotWinAfterSpectatorIsEnteredAndLeftAgain() throws Exception {
        UnlimitedNameTags plugin = mock(UnlimitedNameTags.class);
        NameTagManager nameTags = mock(NameTagManager.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Player player = mock(Player.class);
        AtomicReference<GameMode> currentMode = new AtomicReference<>(GameMode.SPECTATOR);

        when(plugin.getNametagManager()).thenReturn(nameTags);
        when(plugin.getTaskScheduler()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenAnswer(ignored -> currentMode.get());
        when(player.isOnline()).thenReturn(true);

        PlayerListener listener = allocateListener(plugin);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.CREATIVE));
        currentMode.set(GameMode.CREATIVE);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.SPECTATOR));
        currentMode.set(GameMode.SPECTATOR);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL));

        ArgumentCaptor<Runnable> recoveries = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(2)).runTaskLater(eq(player), recoveries.capture(), eq(1L));
        currentMode.set(GameMode.SURVIVAL);
        reset(nameTags);

        recoveries.getAllValues().get(0).run();
        verifyNoInteractions(nameTags);

        recoveries.getAllValues().get(1).run();
        verify(nameTags).unblockPlayer(player);
        verify(nameTags).showToTrackedPlayers(player);
    }

    private static PlayerListener allocateListener(UnlimitedNameTags plugin) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        PlayerListener listener = (PlayerListener) ((Unsafe) unsafeField.get(null))
                .allocateInstance(PlayerListener.class);
        setField(listener, "plugin", plugin);
        setFieldIfPresent(listener, "gameModeRecoveryRunIds", new ConcurrentHashMap<UUID, UUID>());
        return listener;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setFieldIfPresent(Object target, String name, Map<UUID, UUID> value) throws Exception {
        try {
            setField(target, name, value);
        } catch (NoSuchFieldException ignored) {
            // Field is introduced by the production fix; baseline must still run RED.
        }
    }
}

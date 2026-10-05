package org.alexdev.unlimitednametags.listeners;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.alexdev.unlimitednametags.TestInstances;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;

class PlayerListenerRecoveryTest {
    private static MockedStatic<RegistryAccess> registryAccess;
    @BeforeAll static void initializePotionRegistry() {
        registryAccess = TestInstances.potionRegistry();
    }
    @AfterAll static void clearPotionRegistry() { registryAccess.close(); }
    private PlayerListener listener;
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final NameTagManager tags = mock(NameTagManager.class);
    private final TrackerManager tracker = mock(TrackerManager.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final Map<UUID, Player> online = new ConcurrentHashMap<>();

    @BeforeEach void setup() throws Exception {
        listener = TestInstances.allocate(PlayerListener.class);
        UnlimitedNameTags plugin = TestInstances.allocate(UnlimitedNameTags.class);
        TestInstances.set(plugin, "nametagManager", tags);
        TestInstances.set(plugin, "trackerManager", tracker);
        TestInstances.set(plugin, "taskScheduler", scheduler);
        TestInstances.set(listener, "plugin", plugin);
        TestInstances.set(listener, "onlinePlayers", online);
        TestInstances.set(listener, "poseRetryRunIds", new ConcurrentHashMap<>());
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        online.put(id, player);
        when(tags.getPacketDisplays(player)).thenReturn(java.util.List.of(mock(org.alexdev.unlimitednametags.packet.PaperNametagRow.class)));
    }
    private void recover() throws Exception {
        Method method = PlayerListener.class.getDeclaredMethod("recoverNametagVisibility", Player.class);
        method.setAccessible(true);
        method.invoke(listener, player);
    }

    private void poseEvent() throws Exception {
        var event = new org.bukkit.event.entity.EntityPoseChangeEvent(player, org.bukkit.entity.Pose.STANDING);
        var method = PlayerListener.class.getDeclaredMethod("onPoseChange", org.bukkit.event.entity.EntityPoseChangeEvent.class);
        method.invoke(listener, event);
    }

    @Test void poseRetryUsesOwnersSchedulerAndAuthoritativeNewPose() throws Exception {
        var packets = mock(org.alexdev.unlimitednametags.packet.PacketManager.class);
        TestInstances.set(listener.getPlugin(), "packetManager", packets);
        when(player.getPose()).thenReturn(org.bukkit.entity.Pose.SWIMMING);
        poseEvent();
        verifyNoInteractions(packets);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        when(player.getPose()).thenReturn(org.bukkit.entity.Pose.SNEAKING);
        task.getValue().run();
        var writes = mockingDetails(packets).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendPoseSnapshot")).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, writes.size());
        org.junit.jupiter.api.Assertions.assertEquals(com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.CROUCHING,
                writes.get(0).getArgument(1));
        verify(player, never()).setPose(any());
    }

    @Test void poseRetryRejectsReplacementSession() throws Exception {
        poseEvent();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        online.put(id, mock(Player.class));
        task.getValue().run();
        verify(player, never()).getPose();
    }

    @Test void poseRetryRejectsHiddenOwner() throws Exception {
        poseEvent();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        when(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        task.getValue().run();
        verify(player, never()).getPose();
    }

    @Test void latestTransitionInvalidatesBothPendingCallbacksAndQueuedWrites() throws Exception {
        var packets = mock(org.alexdev.unlimitednametags.packet.PacketManager.class);
        TestInstances.set(listener.getPlugin(), "packetManager", packets);
        when(player.getPose()).thenReturn(org.bukkit.entity.Pose.SWIMMING);
        poseEvent();
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), tasks.capture(), eq(1L));
        tasks.getValue().run();
        var write = mockingDetails(packets).getInvocations().iterator().next();
        java.util.function.BooleanSupplier valid = write.getArgument(2);
        org.junit.jupiter.api.Assertions.assertTrue(valid.getAsBoolean());
        poseEvent();
        org.junit.jupiter.api.Assertions.assertFalse(valid.getAsBoolean());
        tasks.getValue().run();
        org.junit.jupiter.api.Assertions.assertEquals(1, mockingDetails(packets).getInvocations().size());
    }

    @Test void poseRetryPreservesLegitimateSwimmingAndDoesNotSetServerPose() throws Exception {
        var packets = mock(org.alexdev.unlimitednametags.packet.PacketManager.class);
        TestInstances.set(listener.getPlugin(), "packetManager", packets);
        when(player.getPose()).thenReturn(org.bukkit.entity.Pose.SWIMMING);
        poseEvent();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        task.getValue().run();
        verify(packets).sendPoseSnapshot(eq(player), eq(com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.SWIMMING), any());
        verify(player, never()).setPose(any());
    }

    @Test void ownerWithoutNametagRowsDoesNotReceivePoseRetry() throws Exception {
        when(tags.getPacketDisplays(player)).thenReturn(java.util.List.of());
        poseEvent();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        task.getValue().run();
        verify(player, never()).getPose();
    }
    @Test void currentVisibleSessionReconcilesBeforeShowing() throws Exception {
        recover();
        var order = inOrder(tracker, tags);
        order.verify(tracker).reconcileTrackedState(player);
        order.verify(tags).showToTrackedPlayers(player);
        order.verify(tags).updateDisplaysForPlayer(player);
    }
    @Test void previousConnectionCannotRecoverReplacement() throws Exception {
        online.put(id, mock(Player.class));
        recover();
        verifyNoInteractions(tags, tracker);
    }
    @Test void invisibleOwnerCannotRecover() throws Exception {
        when(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        recover();
        verifyNoInteractions(tags, tracker);
    }
    @Test void deadOwnerCannotRecover() throws Exception {
        when(player.isDead()).thenReturn(true);
        recover();
        verifyNoInteractions(tags, tracker);
    }
    @Test void spectatorExitWaitsUntilTheNewModeTakesEffect() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL));
        verifyNoInteractions(tags, tracker);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        task.getValue().run();
        verify(tags).unblockPlayer(player);
        verify(tracker).reconcileTrackedState(player);
        verify(tags).showToTrackedPlayers(player);
    }
    @Test void pendingSpectatorExitRejectsReconnect() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.SURVIVAL));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(1L));
        online.put(id, mock(Player.class));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        task.getValue().run();
        verifyNoInteractions(tags, tracker);
    }
    @Test void enteringSpectatorHidesImmediately() {
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.SPECTATOR));
        verify(tags).removeAllViewers(player);
        verifyNoInteractions(tracker, scheduler);
    }
    private Runnable invisibilityRecovery() {
        EntityPotionEffectEvent event = mock(EntityPotionEffectEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getAction()).thenReturn(EntityPotionEffectEvent.Action.REMOVED);
        when(event.getOldEffect()).thenReturn(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        listener.onPotion(event);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(player), task.capture(), eq(3L));
        return task.getValue();
    }
    @Test void invisibilityRemovalRecoversOnOwnerSchedulerAfterEffectEnds() {
        when(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        Runnable task = invisibilityRecovery();
        verifyNoInteractions(tags, tracker);
        when(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(false);
        task.run();
        verify(tags).unblockPlayer(player);
        verify(tracker).reconcileTrackedState(player);
        verify(tags).showToTrackedPlayers(player);
    }
    @Test void renewedInvisibilityRejectsPendingRecovery() {
        Runnable task = invisibilityRecovery();
        when(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        task.run();
        verifyNoInteractions(tags, tracker);
    }
}

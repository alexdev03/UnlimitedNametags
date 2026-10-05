package org.alexdev.unlimitednametags.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import io.papermc.paper.registry.RegistryAccess;
import org.alexdev.unlimitednametags.TestInstances;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.listeners.PlayerListener;
import org.alexdev.unlimitednametags.listeners.TrackerManager;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.packet.PacketManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitNametagVisibilityTest {
    private static MockedStatic<RegistryAccess> registry;
    @BeforeAll static void installRegistry() { registry = TestInstances.potionRegistry(); }
    @AfterAll static void removeRegistry() { registry.close(); }
    private final UUID ownerId = UUID.randomUUID(), viewerId = UUID.randomUUID();
    private final Player owner = mock(Player.class), viewer = mock(Player.class);
    private final World world = mock(World.class);
    private final NameTagManager tags = mock(NameTagManager.class);
    private final Settings settings = new Settings();
    private BukkitNametagPlatform platform;

    @BeforeEach void setup() throws Exception {
        UnlimitedNameTags plugin = TestInstances.allocate(UnlimitedNameTags.class);
        PlayerListener players = TestInstances.allocate(PlayerListener.class);
        TestInstances.set(players, "onlinePlayers", Map.of(ownerId, owner, viewerId, viewer));
        ConfigManager config = TestInstances.allocate(ConfigManager.class);
        TestInstances.set(config, "settings", settings);
        PacketManager packets = mock(PacketManager.class);
        when(packets.knowsOwner(any(), eq(owner))).thenReturn(true);
        TestInstances.set(plugin, "playerListener", players);
        TestInstances.set(plugin, "configManager", config);
        TestInstances.set(plugin, "nametagManager", tags);
        TestInstances.set(plugin, "trackerManager", mock(TrackerManager.class));
        TestInstances.set(plugin, "packetManager", packets);
        TestInstances.set(plugin, "hooks", Map.of());
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(owner.isOnline()).thenReturn(true);
        when(viewer.isOnline()).thenReturn(true);
        when(owner.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(owner.getWorld()).thenReturn(world);
        when(viewer.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(owner.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(viewer.getLocation()).thenReturn(new Location(world, 4, 64, 0));
        when(viewer.hasLineOfSight(owner)).thenReturn(true);
        when(viewer.canSee(owner)).thenReturn(true);
        when(owner.canSee(owner)).thenReturn(true);
        when(viewer.hasPermission("unt.shownametags")).thenReturn(true);
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        PlayerManager users = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(users);
        when(users.getChannel(any())).thenReturn(new Object());
        when(users.getUser(any())).thenReturn(mock(User.class));
        PacketEvents.setAPI(api);
        platform = new BukkitNametagPlatform(plugin, ownerId);
    }
    @AfterEach void clearProtocol() { PacketEvents.setAPI(null); }
    private String reason() { return platform.nametagShowBlockReason(ownerId, viewerId, true, false); }
    private void mode(Settings.ThroughWallMode mode) throws Exception {
        TestInstances.set(settings.getVisibility(), "throughWallMode", mode);
    }
    @Test void normalVisibleTrackedOwnerIsAllowed() { assertNull(reason()); }
    @Test void hideModeRejectsBlockedSightOnEveryShow() throws Exception {
        mode(Settings.ThroughWallMode.HIDE);
        when(viewer.hasLineOfSight(owner)).thenReturn(false);
        assertEquals("HIDE wall visibility suppresses nametags", reason());
    }
    @Test void hideModeRejectsOutOfRangeAndAllowsItsBoundary() throws Exception {
        mode(Settings.ThroughWallMode.HIDE);
        settings.getVisibility().getThroughWallSettings().setMaxDistance(4);
        assertNull(reason());
        when(viewer.getLocation()).thenReturn(new Location(world, 4.1, 64, 0));
        assertEquals("HIDE wall visibility suppresses nametags", reason());
    }
    @Test void obscuredModeDoesNotRejectBlockedSight() throws Exception {
        mode(Settings.ThroughWallMode.OBSCURED);
        when(viewer.hasLineOfSight(owner)).thenReturn(false);
        assertNull(reason());
    }
    @Test void hiddenViewerPreferenceCannotBeBypassedByShow() {
        when(tags.isHiddenOtherNametags(viewer)).thenReturn(true);
        assertEquals("viewer has hidden other nametags", reason());
    }
    @Test void spectatorDeadAndInvisibleOwnersAreSuppressed() {
        when(owner.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertEquals("owner state suppresses nametags", reason());
        when(owner.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(owner.isDead()).thenReturn(true);
        assertEquals("owner state suppresses nametags", reason());
        when(owner.isDead()).thenReturn(false);
        when(owner.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        assertEquals("owner state suppresses nametags", reason());
    }
    @Test void disabledWorldRejectsShow() {
        settings.getWorlds().setList(java.util.List.of("world"));
        assertEquals("nametags are disabled in the owner's world", reason());
    }
    @Test void ownTagPreferenceRejectsShowWithoutChangingOtherTags() {
        assertEquals("own nametag is disabled", platform.nametagShowBlockReason(ownerId, ownerId, true, false));
        assertNull(reason());
    }
    @Test void crossWorldOwnerIsRejectedBeforeDistanceEvaluation() throws Exception {
        mode(Settings.ThroughWallMode.HIDE);
        World other = mock(World.class);
        when(other.getName()).thenReturn("other");
        when(viewer.getWorld()).thenReturn(other);
        assertEquals("viewer is in world other but owner is in world world", reason());
        verify(viewer, never()).getLocation();
    }
}

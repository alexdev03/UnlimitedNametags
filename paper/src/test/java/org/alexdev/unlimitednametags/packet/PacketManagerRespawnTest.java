package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.player.User;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PacketManagerRespawnTest {

    @Test
    void sameLevelRespawnKeepsKnownEntitiesAndOnlyDropsTheLocalPlayersPassengers() throws Exception {
        final Fixture f = new Fixture();
        f.manager.joined(f.user, "minecraft:overworld");
        final PassengerState state = f.state();
        state.spawn(42);                       // another player the client keeps
        state.setPassengers(42, new int[]{7}); // vanilla passenger of that player
        state.setPassengers(5, new int[]{9});  // passenger of the local player (entity 5)

        assertTrue(f.manager.respawned(f.user, "minecraft:overworld", 5));

        assertSame(state, f.connections.get(f.user), "state must survive a skin/profile refresh");
        assertTrue(state.knows(42));
        assertArrayEquals(new int[]{7}, state.passengers(42));
        assertArrayEquals(new int[0], state.passengers(5));
    }

    @Test
    void respawnIntoAnotherLevelStartsFromScratch() throws Exception {
        final Fixture f = new Fixture();
        f.manager.joined(f.user, "minecraft:overworld");
        f.state().spawn(42);

        assertFalse(f.manager.respawned(f.user, "minecraft:the_nether", 5));
        assertNull(f.connections.get(f.user));

        // The new level is now the reference for the next respawn.
        f.state().spawn(43);
        assertTrue(f.manager.respawned(f.user, "minecraft:the_nether", 5));
        assertTrue(f.connections.get(f.user).knows(43));
    }

    @Test
    void unknownLevelIsTreatedAsANewLevel() throws Exception {
        final Fixture f = new Fixture();
        f.state().spawn(42);
        assertFalse(f.manager.respawned(f.user, "minecraft:overworld", 5), "no JOIN_GAME seen yet");
        assertNull(f.connections.get(f.user));

        f.manager.joined(f.user, "minecraft:overworld");
        assertFalse(f.manager.respawned(f.user, null, 5), "packet without a level name");
        f.state().spawn(42);
        assertFalse(f.manager.respawned(f.user, "minecraft:overworld", 5), "level was forgotten");
    }

    @Test
    void disconnectForgetsTheLevel() throws Exception {
        final Fixture f = new Fixture();
        f.manager.joined(f.user, "minecraft:overworld");
        f.manager.forget(f.user);
        assertFalse(f.manager.respawned(f.user, "minecraft:overworld", 5));
    }

    private static final class Fixture {
        final PacketManager manager;
        final Map<User, PassengerState> connections = new ConcurrentHashMap<>();
        final User user = mock(User.class);

        Fixture() throws Exception {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            // Skip the constructor (EntityLib bootstrap); the respawn bookkeeping is pure state.
            manager = (PacketManager) ((Unsafe) unsafeField.get(null)).allocateInstance(PacketManager.class);
            set("connections", connections);
            set("levels", new ConcurrentHashMap<>());
            set("rowIds", ConcurrentHashMap.newKeySet());
        }

        PassengerState state() {
            return connections.computeIfAbsent(user, ignored -> new PassengerState());
        }

        private void set(String name, Object value) throws Exception {
            final Field field = PacketManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(manager, value);
        }
    }
}

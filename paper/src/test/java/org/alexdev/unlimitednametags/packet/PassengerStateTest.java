package org.alexdev.unlimitednametags.packet;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PassengerStateTest {
    @Test void preservesExternalOrderAndAppendsOnlySentRowsInAuthoredOrder() {
        PassengerState state = new PassengerState();
        state.spawn(100);
        state.spawn(102);
        state.spawn(101);
        assertArrayEquals(new int[]{8, 7, 102, 101},
                state.compose(new int[]{8, 101, 7, 103}, Set.of(101, 102, 103), List.of(102, 101, 103)));
    }

    @Test void destroyedAndUnsentRowsCannotBeMounted() {
        PassengerState state = new PassengerState();
        state.spawn(101);
        state.destroy(101);
        assertArrayEquals(new int[]{8}, state.compose(new int[]{8}, Set.of(101, 102), List.of(101, 102)));
    }

    @Test void resetDropsEntitiesAndVanillaPassengers() {
        PassengerState state = new PassengerState();
        state.spawn(1);
        state.setPassengers(1, new int[]{2, 3});
        state.clear();
        assertFalse(state.knows(1));
        assertArrayEquals(new int[0], state.passengers(1));
    }

    @Test void destroyingVehicleForgetsItsPassengersAndPassengerReferences() {
        PassengerState state = new PassengerState();
        state.setPassengers(1, new int[]{2, 3});
        state.setPassengers(2, new int[]{4});
        state.destroy(2);
        assertArrayEquals(new int[]{3}, state.passengers(1));
        assertArrayEquals(new int[0], state.passengers(2));
    }

    @Test void viewerStatesAreIndependent() {
        PassengerState a = new PassengerState();
        PassengerState b = new PassengerState();
        a.spawn(10);
        a.setPassengers(1, new int[]{7});
        assertFalse(b.knows(10));
        assertArrayEquals(new int[0], b.passengers(1));
    }
    @Test void lateSpawnCompletionCannotUndoDestroy() {
        PassengerState state = new PassengerState();
        long token = state.beginSpawn(10);
        state.destroy(10);
        assertFalse(state.completeSpawn(10, token));
        assertFalse(state.knows(10));
    }

    @Test void olderSpawnCompletionCannotReplaceNewGeneration() {
        PassengerState state = new PassengerState();
        long old = state.beginSpawn(10);
        long current = state.beginSpawn(10);
        assertFalse(state.completeSpawn(10, old));
        assertTrue(state.completeSpawn(10, current));
        assertTrue(state.knows(10));
    }

    @Test void resetInvalidatesPendingSpawns() {
        PassengerState state = new PassengerState();
        long token = state.beginSpawn(10);
        state.clear();
        assertFalse(state.completeSpawn(10, token));
    }

}

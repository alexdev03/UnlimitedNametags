package org.alexdev.unlimitednametags.packet;

import org.alexdev.unlimitednametags.TestInstances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class PacketManagerCleanupTest {
    private PacketManager manager;
    @BeforeEach void setup() throws Exception {
        manager = TestInstances.allocate(PacketManager.class);
        TestInstances.set(manager, "connections", new ConcurrentHashMap<>());
        TestInstances.set(manager, "rowIds", ConcurrentHashMap.newKeySet());
        TestInstances.set(manager, "ownerRows", new ConcurrentHashMap<>());
    }

    @Test void ownerRemovalIncludesCurrentAndRetiredRowsWithoutDuplicates() {
        manager.registerRow(1, 101);
        manager.registerRow(1, 102);
        manager.registerRow(1, 101);
        manager.registerRow(2, 201);
        int[] ids = manager.includeOwnedRows(new int[]{8, 1, 101, 8});
        assertEquals(8, ids[0]);
        assertEquals(1, ids[1]);
        assertEquals(4, ids.length);
        assertEquals(Set.of(8, 1, 101, 102), Arrays.stream(ids).boxed().collect(Collectors.toSet()));
    }

    @Test void viewerReleaseDoesNotLoseRetiredOwnerRows() {
        manager.registerRow(1, 101);
        manager.removePassenger(101);
        assertArrayEquals(new int[]{1, 101}, manager.includeOwnedRows(new int[]{1}));
    }

    @Test void unrelatedEntityRemovalsStayUnchanged() {
        manager.registerRow(1, 101);
        assertArrayEquals(new int[]{8, 9}, manager.includeOwnedRows(new int[]{8, 9}));
        assertArrayEquals(new int[0], manager.includeOwnedRows(new int[0]));
    }

    @Test void shutdownClearsRetiredAssociations() {
        manager.registerRow(1, 101);
        manager.close();
        assertArrayEquals(new int[]{1}, manager.includeOwnedRows(new int[]{1}));
    }
}

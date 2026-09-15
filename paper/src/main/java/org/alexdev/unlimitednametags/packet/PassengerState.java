package org.alexdev.unlimitednametags.packet;

import java.util.*;

/** Per-connection state observed at the outbound packet boundary, not a client acknowledgement. */
final class PassengerState {
    private final Map<Integer, Long> spawned = new HashMap<>();
    private final Map<Integer, Long> pendingSpawns = new HashMap<>();
    private long sequence;
    private final Map<Integer, int[]> passengers = new HashMap<>();

    synchronized void spawn(int id) { completeSpawn(id, beginSpawn(id)); }
    synchronized long beginSpawn(int id) {
        long token = ++sequence;
        spawned.remove(id);
        pendingSpawns.put(id, token);
        return token;
    }
    synchronized boolean completeSpawn(int id, long token) {
        if (!pendingSpawns.remove(id, token)) return false;
        spawned.put(id, token);
        return true;
    }
    synchronized boolean knows(int id) { return spawned.containsKey(id); }

    synchronized void destroy(int id) {
        spawned.remove(id);
        pendingSpawns.remove(id);
        passengers.remove(id);
        passengers.replaceAll((owner, ids) -> Arrays.stream(ids).filter(passenger -> passenger != id).toArray());
    }

    synchronized void clear() {
        spawned.clear();
        pendingSpawns.clear();
        passengers.clear();
    }

    synchronized void setPassengers(int owner, int[] ids) { passengers.put(owner, ids.clone()); }
    synchronized int[] passengers(int owner) { return passengers.getOrDefault(owner, new int[0]).clone(); }

    synchronized int[] compose(int[] vanilla, Set<Integer> ownedRows, List<Integer> visibleRows) {
        List<Integer> result = new ArrayList<>();
        for (int id : vanilla) {
            if (!ownedRows.contains(id)) result.add(id);
        }
        for (int id : visibleRows) {
            if (spawned.containsKey(id) && !result.contains(id)) result.add(id);
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
}

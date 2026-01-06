package org.alexdev.unlimitednametags.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentSetMultimap<K, V> {

    private final ConcurrentHashMap<K, ConcurrentLinkedQueue<V>> map = new ConcurrentHashMap<>();

    /**
     * Adds the value to the specified key.
     * If the value is already present for the key, it will not be added.
     *
     * @param key   the key
     * @param value the value to add
     * @return true if the value was added, false otherwise
     */
    public boolean put(K key, V value) {
        // Create a new queue if one does not already exist for the key
        ConcurrentLinkedQueue<V> queue = map.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        // Ensure uniqueness by synchronizing on the queue for this key
        synchronized (queue) {
            if (!queue.contains(value)) {
                return queue.add(value);
            }
        }
        return false;
    }

    /**
     * Adds all values from the provided collection to the specified key.
     * Only adds values that are not already associated with the key.
     *
     * @param key    the key
     * @param values the collection of values to add
     * @return true if at least one value was added, false otherwise
     */
    public boolean putAll(K key, Collection<? extends V> values) {
        // Create a new queue if one does not already exist for the key
        ConcurrentLinkedQueue<V> queue = map.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        boolean changed = false;
        synchronized (queue) {
            for (V value : values) {
                if (!queue.contains(value)) {
                    queue.add(value);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Returns a set of values associated with the specified key.
     *
     * @param key the key
     * @return a Set containing the associated values, or an empty Set if the key does not exist
     */
    public Set<V> get(K key) {
        ConcurrentLinkedQueue<V> queue = map.get(key);
        if (queue == null) {
            return new HashSet<>();
        }
        // Create a HashSet of the values; synchronize to avoid concurrent modification issues
        synchronized (queue) {
            return new HashSet<>(queue);
        }
    }

    /**
     * Removes the specified value associated with the key.
     * If the queue becomes empty after removal, the key is removed from the map.
     *
     * @param key   the key
     * @param value the value to remove
     * @return true if the value was removed, false otherwise
     */
    public boolean remove(K key, V value) {
        ConcurrentLinkedQueue<V> queue = map.get(key);
        if (queue == null) {
            return false;
        }
        synchronized (queue) {
            boolean removed = queue.remove(value);
            if (queue.isEmpty()) {
                map.remove(key, queue);
            }
            return removed;
        }
    }

    /**
     * Removes all values associated with the specified key.
     *
     * @param key the key
     * @return a Set containing the removed values, or an empty Set if the key did not exist
     */
    public Set<V> removeAll(K key) {
        ConcurrentLinkedQueue<V> queue = map.remove(key);
        if (queue == null) {
            return new HashSet<>();
        }
        synchronized (queue) {
            return new HashSet<>(queue);
        }
    }

    /**
     * Removes all key-value associations in the multimap.
     */
    public void clear() {
        map.clear();
    }

    /**
     * Returns a collection view of all values present in the multimap.
     * Note: This is a snapshot of the values at the time of invocation.
     *
     * @return a Collection containing all values from all keys
     */
    public Collection<V> values() {
        Collection<V> allValues = new ArrayList<>();
        for (ConcurrentLinkedQueue<V> queue : map.values()) {
            synchronized (queue) {
                allValues.addAll(queue);
            }
        }
        return allValues;
    }

    /**
     * Returns the set of keys present in the multimap.
     *
     * @return a Set containing all keys
     */
    public Set<K> keySet() {
        return map.keySet();
    }

    /**
     * Returns a set of key-value pairs in the multimap.
     * Each entry consists of a key and the corresponding set of values.
     *
     * @return a Set of Map.Entry containing keys and their associated value sets
     */
    public Set<java.util.Map.Entry<K, Set<V>>> entrySet() {
        Set<java.util.Map.Entry<K, Set<V>>> entries = new HashSet<>();
        for (K key : map.keySet()) {
            entries.add(new java.util.AbstractMap.SimpleEntry<>(key, get(key)));
        }
        return entries;
    }

    //forEach <key, Set<value>>
    public void forEach(java.util.function.BiConsumer<K, Set<V>> action) {
        for (K key : map.keySet()) {
            action.accept(key, get(key));
        }
    }

    public boolean containsEntry(K key, V value) {
        ConcurrentLinkedQueue<V> queue = map.get(key);
        if (queue == null) {
            return false;
        }
        synchronized (queue) {
            return queue.contains(value);
        }
    }
}


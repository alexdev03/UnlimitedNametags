package org.alexdev.unlimitednametags.data;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * High-performance thread-safe Multimap implementation optimized for concurrent access.
 * Uses ConcurrentHashMap for both the main map and value collections.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
@SuppressWarnings("unused")
public class ConcurrentMultimap<K, V> {

    private final ConcurrentMap<K, Set<V>> map;
    private final Supplier<Set<V>> setSupplier;

    /**
     * Creates a new ConcurrentMultimap with ConcurrentHashMap.newKeySet() as the set supplier.
     */
    public ConcurrentMultimap() {
        this(ConcurrentHashMap::newKeySet);
    }

    /**
     * Creates a new ConcurrentMultimap with a custom set supplier.
     *
     * @param setSupplier supplier for creating new value sets (must be thread-safe)
     */
    public ConcurrentMultimap(@NotNull Supplier<Set<V>> setSupplier) {
        this.map = new ConcurrentHashMap<>();
        this.setSupplier = setSupplier;
    }

    /**
     * Creates a new ConcurrentMultimap with an expected size hint.
     *
     * @param expectedSize the expected number of keys
     */
    public ConcurrentMultimap(int expectedSize) {
        this(expectedSize, ConcurrentHashMap::newKeySet);
    }

    /**
     * Creates a new ConcurrentMultimap with an expected size hint and custom set supplier.
     *
     * @param expectedSize the expected number of keys
     * @param setSupplier supplier for creating new value sets (must be thread-safe)
     */
    public ConcurrentMultimap(int expectedSize, @NotNull Supplier<Set<V>> setSupplier) {
        this.map = new ConcurrentHashMap<>(expectedSize);
        this.setSupplier = setSupplier;
    }

    /**
     * Associates the specified value with the specified key.
     *
     * @param key the key
     * @param value the value to add
     * @return true if the value was added, false if it was already present
     */
    public boolean put(@NotNull K key, @NotNull V value) {
        return map.computeIfAbsent(key, k -> setSupplier.get()).add(value);
    }

    /**
     * Adds all values to the specified key.
     *
     * @param key the key
     * @param values the values to add
     * @return true if any value was added
     */
    public boolean putAll(@NotNull K key, @NotNull Collection<? extends V> values) {
        if (values.isEmpty()) {
            return false;
        }
        return map.computeIfAbsent(key, k -> setSupplier.get()).addAll(values);
    }

    /**
     * Returns all values associated with the specified key.
     * The returned collection is a live view - modifications will affect this multimap.
     *
     * @param key the key
     * @return the collection of values, never null (may be empty)
     */
    @NotNull
    public Collection<V> get(@NotNull K key) {
        Set<V> values = map.get(key);
        return values != null ? values : Collections.emptySet();
    }

    /**
     * Returns all values associated with the specified key as a defensive copy.
     *
     * @param key the key
     * @return a new collection containing all values
     */
    @NotNull
    public Collection<V> getCopy(@NotNull K key) {
        Set<V> values = map.get(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    /**
     * Removes a single key-value pair.
     *
     * @param key the key
     * @param value the value to remove
     * @return true if the value was removed
     */
    public boolean remove(@NotNull K key, @NotNull V value) {
        Set<V> values = map.get(key);
        if (values == null) {
            return false;
        }

        boolean removed = values.remove(value);

        // Clean up empty sets to prevent memory leaks
        if (removed && values.isEmpty()) {
            map.remove(key, values);
        }

        return removed;
    }

    /**
     * Removes all values associated with the specified key.
     *
     * @param key the key
     * @return the collection of values that were removed, never null
     */
    @NotNull
    public Collection<V> removeAll(@NotNull K key) {
        Set<V> removed = map.remove(key);
        return removed != null ? removed : Collections.emptySet();
    }

    /**
     * Replaces all values for a key with the specified values.
     *
     * @param key the key
     * @param values the new values
     * @return the previous values associated with the key
     */
    @NotNull
    public Collection<V> replaceValues(@NotNull K key, @NotNull Collection<? extends V> values) {
        if (values.isEmpty()) {
            return removeAll(key);
        }

        Set<V> newSet = setSupplier.get();
        newSet.addAll(values);

        Set<V> previous = map.put(key, newSet);
        return previous != null ? previous : Collections.emptySet();
    }

    /**
     * Returns true if this multimap contains the specified key.
     *
     * @param key the key
     * @return true if the key exists
     */
    public boolean containsKey(@NotNull K key) {
        return map.containsKey(key);
    }

    /**
     * Returns true if this multimap contains the specified key-value pair.
     *
     * @param key the key
     * @param value the value
     * @return true if the pair exists
     */
    public boolean containsEntry(@NotNull K key, @NotNull V value) {
        Set<V> values = map.get(key);
        return values != null && values.contains(value);
    }

    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return the total number of values across all keys
     */
    public int size() {
        return map.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Returns the number of values associated with the specified key.
     *
     * @param key the key
     * @return the number of values for the key
     */
    public int size(@NotNull K key) {
        Set<V> values = map.get(key);
        return values != null ? values.size() : 0;
    }

    /**
     * Returns true if this multimap contains no key-value pairs.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Removes all key-value pairs from this multimap.
     */
    public void clear() {
        map.clear();
    }

    /**
     * Returns a set view of all keys in this multimap.
     *
     * @return the set of keys
     */
    @NotNull
    public Set<K> keySet() {
        return map.keySet();
    }

    /**
     * Returns a collection view of all values in this multimap.
     * This creates a new collection on each call.
     *
     * @return all values
     */
    @NotNull
    public Collection<V> values() {
        List<V> allValues = new ArrayList<>();
        for (Set<V> values : map.values()) {
            allValues.addAll(values);
        }
        return allValues;
    }

    /**
     * Returns a collection view of all entries in this multimap.
     *
     * @return all key-value pairs as Map.Entry objects
     */
    @NotNull
    public Collection<Map.Entry<K, V>> entries() {
        List<Map.Entry<K, V>> entries = new ArrayList<>();
        for (Map.Entry<K, Set<V>> mapEntry : map.entrySet()) {
            K key = mapEntry.getKey();
            for (V value : mapEntry.getValue()) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
            }
        }
        return entries;
    }

    /**
     * Returns a map view where each key is associated with a collection of values.
     * The returned map is a snapshot - modifications will not affect this multimap.
     *
     * @return a map representation
     */
    @NotNull
    public Map<K, Collection<V>> asMap() {
        Map<K, Collection<V>> result = new HashMap<>();
        for (Map.Entry<K, Set<V>> entry : map.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Executes the given action for each key-value pair.
     *
     * @param action the action to perform
     */
    public void forEach(@NotNull java.util.function.BiConsumer<? super K, ? super V> action) {
        for (Map.Entry<K, Set<V>> entry : map.entrySet()) {
            K key = entry.getKey();
            for (V value : entry.getValue()) {
                action.accept(key, value);
            }
        }
    }

    /**
     * Removes a specific value from all keys.
     * Optimized for performance - avoids iteration over all values.
     *
     * @param value the value to remove from all keys
     * @return the number of keys from which the value was removed
     */
    public int removeValueFromAll(@NotNull V value) {
        int removedCount = 0;
        for (Map.Entry<K, Set<V>> entry : map.entrySet()) {
            Set<V> values = entry.getValue();
            if (values.remove(value)) {
                removedCount++;
                // Clean up empty sets
                if (values.isEmpty()) {
                    map.remove(entry.getKey(), values);
                }
            }
        }
        return removedCount;
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConcurrentMultimap)) return false;
        ConcurrentMultimap<?, ?> that = (ConcurrentMultimap<?, ?>) o;
        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }
}

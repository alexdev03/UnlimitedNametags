package org.alexdev.unlimitednametags.nametags;

import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NameTagManagerCreationTest {
    @Test
    void removalClearsPendingCreationAndLateCompletionPreservesReplacement() throws Exception {
        // Bypass the constructor's Bukkit scheduling/storage; exercise real lifecycle methods.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        NameTagManager manager = (NameTagManager) ((Unsafe) unsafeField.get(null))
                .allocateInstance(NameTagManager.class);
        UUID owner = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return owner;
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });
        ConcurrentHashMap<UUID, CopyOnWriteArrayList<PacketNameTag>> nameTags = new ConcurrentHashMap<>();
        Set<UUID> creating = new HashSet<>();
        Map<UUID, AtomicInteger> pending = new HashMap<>();
        setField(manager, "nameTags", nameTags);
        setField(manager, "creating", creating);
        setField(manager, "pendingRowCreations", pending);
        setField(manager, "entityIdToDisplay", new HashMap<>());

        CopyOnWriteArrayList<PacketNameTag> oldRows = new CopyOnWriteArrayList<>();
        nameTags.put(owner, oldRows);
        creating.add(owner);
        pending.put(owner, new AtomicInteger(1));
        Method guard = NameTagManager.class.getDeclaredMethod("runIfCurrentRows",
                Player.class, CopyOnWriteArrayList.class, Runnable.class);
        guard.setAccessible(true);
        Method finish = NameTagManager.class.getDeclaredMethod("finishRowCreation", UUID.class);
        finish.setAccessible(true);
        AtomicInteger callbacks = new AtomicInteger();
        CompletableFuture<Void> resolution = new CompletableFuture<>();
        CompletableFuture<Void> completion = resolution.thenRun(() -> {
            try {
                guard.invoke(manager, player, oldRows, (Runnable) () -> {
                    callbacks.incrementAndGet();
                    try {
                        finish.invoke(manager, owner);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                });
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });

        manager.removePlayer(player);
        assertAll(
                () -> assertFalse(nameTags.containsKey(owner)),
                () -> assertFalse(creating.contains(owner), "removed owner must be allowed to create again"),
                () -> assertFalse(pending.containsKey(owner), "removed generation must release pending count"));

        CopyOnWriteArrayList<PacketNameTag> replacement = new CopyOnWriteArrayList<>();
        AtomicInteger replacementPending = new AtomicInteger(1);
        nameTags.put(owner, replacement);
        creating.add(owner);
        pending.put(owner, replacementPending);
        resolution.complete(null);
        completion.join();

        assertEquals(0, callbacks.get(), "stale completion must not run finishRowCreation");
        assertSame(replacement, nameTags.get(owner));
        assertTrue(creating.contains(owner));
        assertSame(replacementPending, pending.get(owner));
        assertEquals(1, replacementPending.get());
    }

    private static void setField(NameTagManager manager, String name, Object value) throws Exception {
        Field field = NameTagManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}

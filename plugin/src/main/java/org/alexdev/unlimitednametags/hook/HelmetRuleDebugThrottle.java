package org.alexdev.unlimitednametags.hook;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits helmet-rule debug spam while offsets are recomputed often.
 */
public final class HelmetRuleDebugThrottle {

    private static final long MIN_COOLDOWN_MS = 1000L;
    private static final ConcurrentHashMap<UUID, Long> NEXT_ALLOWED_MS = new ConcurrentHashMap<>();

    private HelmetRuleDebugThrottle() {
    }

    /**
     * @return {@code true} if this call should emit a full debug burst for the player.
     */
    public static boolean tryConsume(@NotNull final UUID playerId, final long cooldownMs) {
        final long cd = Math.max(MIN_COOLDOWN_MS, cooldownMs);
        final long now = System.currentTimeMillis();
        final Long prev = NEXT_ALLOWED_MS.get(playerId);
        if (prev != null && now < prev) {
            return false;
        }
        NEXT_ALLOWED_MS.put(playerId, now + cd);
        return true;
    }

    public static void remove(@NotNull final UUID playerId) {
        NEXT_ALLOWED_MS.remove(playerId);
    }

    public static void clearAll() {
        NEXT_ALLOWED_MS.clear();
    }
}

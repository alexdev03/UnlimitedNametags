package org.alexdev.unlimitednametags.nametags;

import org.alexdev.unlimitednametags.config.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameTagManagerTest {

    @Test
    void compactStackReservesConfiguredTextRowWhenResolvedTextIsBlank() {
        final Settings.DisplayGroup nameRow = Settings.DisplayGroup.builder()
                .line("%player_name%")
                .build();
        final Settings.DisplayGroup healthRow = Settings.DisplayGroup.builder()
                .line("%player_health%")
                .build();

        assertTrue(CompactStackVisibility.shouldReserveTextRowStackSpace(nameRow));
        assertTrue(CompactStackVisibility.shouldReserveTextRowStackSpace(healthRow));
    }

    @Test
    void compactStackStillIgnoresFullyEmptyTextRows() {
        final Settings.DisplayGroup emptyRow = Settings.DisplayGroup.builder()
                .line("")
                .build();

        assertFalse(CompactStackVisibility.shouldReserveTextRowStackSpace(emptyRow));
    }
}

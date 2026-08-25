package org.alexdev.unlimitednametags.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSelectionTest {

    @Test
    void blacklistDisablesOnlyListedWorlds() {
        WorldSelection selection = new WorldSelection(WorldSelection.Mode.BLACKLIST, List.of("lobby", "minigames"));

        assertTrue(selection.isEnabled("survival"));
        assertFalse(selection.isEnabled("lobby"));
    }

    @Test
    void whitelistEnablesOnlyListedWorlds() {
        WorldSelection selection = new WorldSelection(WorldSelection.Mode.WHITELIST, List.of("survival"));

        assertTrue(selection.isEnabled("survival"));
        assertFalse(selection.isEnabled("lobby"));
    }

    @Test
    void emptyBlacklistEnablesAllAndEmptyWhitelistDisablesAll() {
        assertTrue(new WorldSelection(WorldSelection.Mode.BLACKLIST, List.of()).isEnabled("anywhere"));
        assertFalse(new WorldSelection(WorldSelection.Mode.WHITELIST, List.of()).isEnabled("anywhere"));
    }
}

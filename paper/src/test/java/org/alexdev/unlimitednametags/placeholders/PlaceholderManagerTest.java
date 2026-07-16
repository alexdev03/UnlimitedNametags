package org.alexdev.unlimitednametags.placeholders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderManagerTest {

    @Test
    void relationalLegacyPrefixIsInsertedBeforeFollowingLiteralText() {
        final String result = PlaceholderManager.replaceRelationalPlaceholders(
                "%rel_prefix%Alice",
                placeholder -> {
                    assertEquals("%rel_prefix%", placeholder);
                    return "&c";
                }
        );

        assertEquals("&cAlice", result);
    }
}

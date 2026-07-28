package org.alexdev.unlimitednametags.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplayGroupItemSourceTest {

    @Test
    void eachItemSourceRoundTripsThroughBuilderAndCopy() {
        final Settings.DisplayGroup cmd = Settings.DisplayGroup.builder()
                .displayType(NametagDisplayType.ITEM)
                .itemMaterial("PAPER")
                .customModelData(42)
                .build();
        final Settings.DisplayGroup model = Settings.DisplayGroup.builder()
                .displayType(NametagDisplayType.ITEM)
                .itemMaterial("PAPER")
                .itemModel("example:badge")
                .build();
        final Settings.DisplayGroup nexo = Settings.DisplayGroup.builder()
                .displayType(NametagDisplayType.ITEM)
                .nexoId("crown")
                .build();

        assertEquals(42, Settings.DisplayGroup.builder(cmd).build().customModelData());
        assertEquals("example:badge", Settings.DisplayGroup.builder(model).build().itemModel());
        assertEquals("crown", Settings.DisplayGroup.builder(nexo).build().nexoId());
        assertNull(nexo.blockMaterial());
    }

    @Test
    void previousDisplayGroupConstructorRemainsAvailable() {
        final Settings.DisplayGroup group = new Settings.DisplayGroup(
                java.util.List.of(), null, 1.0f, 0.0f, null, false, NametagDisplayType.ITEM,
                "PAPER", null, "HEAD", null, null, null, null, null);

        assertEquals("PAPER", group.itemMaterial());
        assertNull(group.customModelData());
        assertNull(group.itemModel());
        assertNull(group.nexoId());
    }

    @Test
    void rejectsBothVanillaModelSourcesWithoutNexo() {
        final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Settings.DisplayGroup.builder()
                        .displayType(NametagDisplayType.ITEM)
                        .itemMaterial("PAPER")
                        .customModelData(42)
                        .itemModel("example:badge")
                        .build());

        assertEquals("ITEM display group cannot set both customModelData and itemModel", error.getMessage());
    }
}
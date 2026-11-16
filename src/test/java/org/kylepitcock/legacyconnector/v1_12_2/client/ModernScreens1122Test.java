package org.kylepitcock.legacyconnector.v1_12_2.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import legacyconnector.v1_12_2.net.minecraft.client.gui.GuiButton;
import legacyconnector.v1_12_2.net.minecraft.client.gui.GuiCreateWorld;
import legacyconnector.v1_12_2.net.minecraft.client.gui.GuiScreen;
import org.junit.jupiter.api.Test;

class ModernScreens1122Test {
    @Test
    void createWorldViewMatchesTheLegacyTypeUsedByForgeSubscribers() {
        Class<?> view = ModernScreens1122.CreateWorldView.class;

        assertTrue(GuiCreateWorld.class.isAssignableFrom(view));
        assertTrue(ScreenEvents1122.Modern.class.isAssignableFrom(view));
    }

    @Test
    void createWorldOptionsAnchorIsOnlyAnEventLayoutPlaceholder() {
        GuiButton anchor = ScreenEvents1122.worldOptionsAnchor(new GuiCreateWorld(null), 480, 270, "More World Options...");

        assertEquals(Integer.MIN_VALUE, anchor.id);
        assertEquals(24, anchor.x);
        assertEquals(184, anchor.y);
        assertEquals(150, anchor.width);
        assertEquals("More World Options...", anchor.displayString);
        assertTrue(ScreenEvents1122.isWorldOptionsAnchor(anchor));
        assertNull(ScreenEvents1122.worldOptionsAnchor(new GuiScreen(), 640, 360, "More World Options..."));
    }
}

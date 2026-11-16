package org.kylepitcock.legacyconnector.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ButtonPlacementTest {
    @Test
    void buttonOnTopOfAVanillaButtonMovesToTheNearestFreeSpot() {
        List<int[]> vanilla = List.of(new int[] {165, 92, 150, 20}, new int[] {325, 92, 150, 20}, new int[] {165, 116, 150, 20});
        int[] wanted = {165, 100, 150, 20};
        assertTrue(ButtonPlacement.overlapsAny(wanted, vanilla));
        int[] spot = ButtonPlacement.freeSpot(wanted, vanilla, 640, 360);
        assertFalse(ButtonPlacement.overlapsAny(spot, vanilla));
        assertTrue(Math.abs(spot[1] - 100) < 60);
    }

    @Test
    void worldSettingsButtonMovesClearOfWorldNameControls() {
        List<int[]> controls = List.of(
                new int[] {54, 0, 120, 22},
                new int[] {136, 40, 65, 9},
                new int[] {136, 53, 208, 20},
                new int[] {136, 81, 208, 20},
                new int[] {136, 109, 208, 20},
                new int[] {136, 137, 208, 20});
        int[] spot = ButtonPlacement.freeSpot(new int[] {24, 36, 150, 20}, controls, 480, 270);

        assertFalse(ButtonPlacement.overlapsAny(spot, controls));
        assertTrue(spot[1] >= 158);
    }

    @Test
    void freeButtonsAreNotMoved() {
        assertArrayEquals(new int[] {5, 5, 20, 20}, ButtonPlacement.freeSpot(new int[] {5, 5, 20, 20}, List.of(), 640, 360));
    }
}

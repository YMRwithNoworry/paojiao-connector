package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SrpLegacyBlockTimer1122Test {
    @Test
    void usesTheConfiguredStageDelayInTicksAndPreservesTheDefaultForOtherStages() {
        assertEquals(1_200, SrpLegacyBlockTimer1122.getTimer(1, 60, 55, 50));
        assertEquals(1_100, SrpLegacyBlockTimer1122.getTimer(2, 60, 55, 50));
        assertEquals(1_000, SrpLegacyBlockTimer1122.getTimer(3, 60, 55, 50));
        assertEquals(40, SrpLegacyBlockTimer1122.getTimer(0, 60, 55, 50));
        assertEquals(40, SrpLegacyBlockTimer1122.getTimer(4, 60, 55, 50));
    }
}

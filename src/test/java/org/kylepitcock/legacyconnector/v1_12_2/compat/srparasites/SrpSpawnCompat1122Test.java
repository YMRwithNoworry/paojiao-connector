package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SrpSpawnCompat1122Test {
    @Test
    void parsesTheLegacyMaximumAndMinimumCountFields() {
        assertEquals(3, SrpSpawnCompat1122.rollLegacySpawnCount(new String[]{"srparasites:buglin", "3", "3"}, new Random(1)));
        for (int seed = 0; seed < 100; seed++) {
            int count = SrpSpawnCompat1122.rollLegacySpawnCount(
                    new String[]{"srparasites:buglin", "2", "1"}, new Random(seed));
            assertTrue(count == 1 || count == 2);
        }
    }
}

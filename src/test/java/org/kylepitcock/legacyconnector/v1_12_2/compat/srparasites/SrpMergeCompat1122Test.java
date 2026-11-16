package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SrpMergeCompat1122Test {
    @Test
    void selectsTheConfiguredMergeTierBeforeRandomFallback() {
        String[] table = {"srparasites:low;1;2", null, "srparasites:high;3;4"};

        assertEquals("srparasites:high", SrpMergeCompat1122.select(table, 3, false));
    }

    @Test
    void fallsBackToAnAvailableEntryWhenNoTierMatches() {
        String[] table = {"srparasites:first;1;1", null, "srparasites:second;2;2"};

        assertTrue(java.util.List.of("srparasites:first", "srparasites:second")
                .contains(SrpMergeCompat1122.select(table, 9, false)));
    }

    @Test
    void randomModeSkipsNullEntries() {
        String[] table = {null, "srparasites:only;1;1", null};

        assertEquals("srparasites:only", SrpMergeCompat1122.select(table, 0, true));
    }
}

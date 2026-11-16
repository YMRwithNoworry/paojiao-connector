package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SrpLegacyReinforcementChance1122Test {
    @Test
    void returnsTheConfiguredChanceForEachEvolutionPhaseAndZeroOutsideRange() {
        double[] chances = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        assertEquals(0.0, SrpLegacyReinforcementChance1122.getChance((byte) 0, chances[0], chances[1], chances[2],
                chances[3], chances[4], chances[5], chances[6], chances[7], chances[8], chances[9]));
        for (byte phase = 1; phase <= 10; phase++) {
            assertEquals(chances[phase - 1], SrpLegacyReinforcementChance1122.getChance(phase, chances[0], chances[1],
                    chances[2], chances[3], chances[4], chances[5], chances[6], chances[7], chances[8], chances[9]));
        }
        assertEquals(0.0, SrpLegacyReinforcementChance1122.getChance((byte) 11, chances[0], chances[1], chances[2],
                chances[3], chances[4], chances[5], chances[6], chances[7], chances[8], chances[9]));
    }
}

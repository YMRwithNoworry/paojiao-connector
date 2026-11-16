package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import java.util.Random;

/** Shared count parsing for the legacy SRP spawnM entry point. */
public final class SrpSpawnCompat1122 {
    private SrpSpawnCompat1122() {}

    public static int rollLegacySpawnCount(String[] parts, Random random) {
        int maximum = Integer.parseInt(parts[1]);
        int minimum = Integer.parseInt(parts[2]);
        return maximum == minimum ? minimum : random.nextInt(maximum - minimum + 1) + minimum;
    }
}

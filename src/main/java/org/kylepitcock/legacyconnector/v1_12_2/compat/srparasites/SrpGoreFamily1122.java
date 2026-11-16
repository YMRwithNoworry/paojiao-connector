package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import legacyconnector.v1_12_2.net.minecraft.block.Block;

/** Restores the family component removed when SRP split its Gore enum into separate blocks. */
public final class SrpGoreFamily1122 {
    private SrpGoreFamily1122() {}

    public static String getTypeByBlock(Block block, Block goreSim, Block gorePri, Block goreAda, Block gorePure) {
        if (block == gorePri) return ";pri;";
        if (block == goreAda) return ";ada;";
        if (block == gorePure) return ";pure;";
        return ";sim;";
    }
}

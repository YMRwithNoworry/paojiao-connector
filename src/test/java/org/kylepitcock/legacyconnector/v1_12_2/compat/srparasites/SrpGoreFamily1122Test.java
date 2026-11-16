package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;

import legacyconnector.v1_12_2.net.minecraft.block.Block;
import legacyconnector.v1_12_2.net.minecraft.block.material.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SrpGoreFamily1122Test {
    @BeforeAll
    static void bootstrapRegistries() {
        net.neoforged.fml.loading.LoadingModList.of(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void reportsTheParasiteFamilyFromTheSplitGoreBlockIdentity() {
        Block sim = new Block(Material.ROCK);
        Block pri = new Block(Material.ROCK);
        Block ada = new Block(Material.ROCK);
        Block pure = new Block(Material.ROCK);

        assertEquals(";sim;", SrpGoreFamily1122.getTypeByBlock(sim, sim, pri, ada, pure));
        assertEquals(";pri;", SrpGoreFamily1122.getTypeByBlock(pri, sim, pri, ada, pure));
        assertEquals(";ada;", SrpGoreFamily1122.getTypeByBlock(ada, sim, pri, ada, pure));
        assertEquals(";pure;", SrpGoreFamily1122.getTypeByBlock(pure, sim, pri, ada, pure));
    }
}

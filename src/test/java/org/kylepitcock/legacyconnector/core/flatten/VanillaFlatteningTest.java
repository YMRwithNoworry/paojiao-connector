package org.kylepitcock.legacyconnector.core.flatten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VanillaFlatteningTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        var dist = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, net.neoforged.api.distmarker.Dist.CLIENT);
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nearestStatePreservesLegacyMetadataForPropertiesAddedAfterFlattening() {
        var dry = Blocks.OAK_STAIRS.defaultBlockState();
        var waterlogged = dry.setValue(BlockStateProperties.WATERLOGGED, true);
        int expected = VanillaFlattening.legacyKey(dry);

        assertTrue(expected >= 0);
        assertEquals(expected, VanillaFlattening.nearestLegacyKey(waterlogged));
    }

    @Test
    void nearestStateReturnsNoMatchForBlocksAddedAfterFlattening() {
        assertEquals(-1, VanillaFlattening.nearestLegacyKey(Blocks.COPPER_BLOCK.defaultBlockState()));
    }
}

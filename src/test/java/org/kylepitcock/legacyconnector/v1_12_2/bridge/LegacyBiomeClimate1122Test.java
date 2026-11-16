package org.kylepitcock.legacyconnector.v1_12_2.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;
import java.util.Set;
import legacyconnector.v1_12_2.net.minecraftforge.common.BiomeDictionary.Type;
import legacyconnector.v1_12_2.net.minecraftforge.common.BiomeManager.BiomeType;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

class LegacyBiomeClimate1122Test {
    @Test
    void mapsLegacyClimateAndTagsToModernLandAnalogs() {
        assertEquals(Biomes.JUNGLE, LegacyBiomeClimate1122.analogFor(climate(Set.of(Type.JUNGLE), EnumSet.of(BiomeType.WARM), 0.8F, 0.9F,
                0.1F, 0.2F, false, 4, "grass", "dirt")));
        assertEquals(Biomes.SNOWY_PLAINS, LegacyBiomeClimate1122.analogFor(climate(Set.of(), EnumSet.of(BiomeType.ICY), 0.1F, 0.4F,
                0.1F, 0.2F, true, 0, "snow", "dirt")));
        assertEquals(Biomes.BADLANDS, LegacyBiomeClimate1122.analogFor(climate(Set.of(Type.MESA), EnumSet.of(BiomeType.DESERT), 1.0F, 0.0F,
                0.1F, 0.2F, false, 0, "red_sand", "hardened_clay")));
    }

    @Test
    void leavesNonLandBiomesToTheirLegacyDimensionOrWorldType() {
        assertNull(LegacyBiomeClimate1122.analogFor(climate(Set.of(Type.OCEAN), EnumSet.of(BiomeType.WARM), 0.8F, 0.5F,
                -0.8F, 0.1F, false, 0, "gravel", "stone")));
        assertNull(LegacyBiomeClimate1122.analogFor(climate(Set.of(Type.NETHER), EnumSet.of(BiomeType.WARM), 2.0F, 0.0F,
                0.1F, 0.2F, false, 0, "netherrack", "netherrack")));
    }

    private static LegacyBiomeClimate1122.Climate climate(Set<Type> tags, Set<BiomeType> lists, float temperature, float rainfall, float baseHeight,
            float variation, boolean snow, int trees, String top, String filler) {
        return new LegacyBiomeClimate1122.Climate(tags, lists, temperature, rainfall, baseHeight, variation, snow, trees, top, filler);
    }
}

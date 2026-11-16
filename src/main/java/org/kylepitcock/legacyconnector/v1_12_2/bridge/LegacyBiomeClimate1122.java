package org.kylepitcock.legacyconnector.v1_12_2.bridge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import legacyconnector.v1_12_2.net.minecraft.world.biome.Biome;
import legacyconnector.v1_12_2.net.minecraftforge.common.BiomeDictionary;
import legacyconnector.v1_12_2.net.minecraftforge.common.BiomeDictionary.Type;
import legacyconnector.v1_12_2.net.minecraftforge.common.BiomeManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import org.kylepitcock.legacyconnector.core.worldgen.BiomePlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates the 1.12.2 BiomeManager climate lists into native overworld biome placement. */
public final class LegacyBiomeClimate1122 {
    private static final Logger LOG = LoggerFactory.getLogger("LegacyConnector/Biomes");
    private static volatile String lastLogged = "";
    private static boolean registered;

    private LegacyBiomeClimate1122() {}

    /** Called after legacy postInit, when all biome registrations and climate-list edits are complete. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        BiomePlacement.addEntries(LegacyBiomeClimate1122::entries);
    }

    /** Mod biomes explicitly included in at least one 1.12.2 overworld climate list. */
    public static List<BiomePlacement.Entry> entries() {
        List<BiomePlacement.Entry> out = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        for (Biome biome : LegacyBiomes1122.modBiomes()) {
            try {
                Set<BiomeManager.BiomeType> lists = EnumSet.noneOf(BiomeManager.BiomeType.class);
                long weight = 0;
                for (BiomeManager.BiomeType type : BiomeManager.BiomeType.values()) {
                    for (BiomeManager.BiomeEntry entry : BiomeManager.getBiomes(type)) {
                        if (entry.biome != biome) continue;
                        weight += Math.max(0, entry.itemWeight);
                        lists.add(type);
                    }
                }
                String id = BiomeBridge1122.modernId(biome).toString();
                if (weight <= 0) {
                    unplaced.add(id + " (zero climate weight)");
                    continue;
                }
                ResourceKey<net.minecraft.world.level.biome.Biome> analog = analogFor(climate(biome, lists));
                if (analog == null) {
                    unplaced.add(id + " (non-land climate)");
                    continue;
                }
                ResourceKey<net.minecraft.world.level.biome.Biome> key =
                        ResourceKey.create(Registries.BIOME, BiomeBridge1122.modernId(biome));
                out.add(new BiomePlacement.Entry(key, analog, (int) Math.min(Integer.MAX_VALUE, weight)));
            } catch (RuntimeException | LinkageError e) {
                unplaced.add(BiomeBridge1122.modernId(biome) + " (" + e + ")");
            }
        }
        String summary = describe(out) + "|" + unplaced;
        if (!summary.equals(lastLogged)) {
            lastLogged = summary;
            if (!out.isEmpty()) LOG.info("1.12.2 legacy biome climate placement (biome -> vanilla analog, weight): {}", describe(out));
            if (!unplaced.isEmpty()) LOG.info("1.12.2 legacy biomes excluded from overworld placement (zero climate weight or non-land climate): {}", unplaced);
        }
        return out;
    }

    private static Climate climate(Biome biome, Set<BiomeManager.BiomeType> lists) {
        Set<Type> tags = new HashSet<>(BiomeDictionary.getTypes(biome));
        return new Climate(tags, lists, biome.getDefaultTemperature(), biome.getRainfall(), biome.getBaseHeight(), biome.getHeightVariation(),
                biome.getEnableSnow(), biome.decorator == null ? 0 : biome.decorator.treesPerChunk, blockPath(biome.topBlock), blockPath(biome.fillerBlock));
    }

    private static String blockPath(legacyconnector.v1_12_2.net.minecraft.block.state.IBlockState state) {
        if (state == null || state.getBlock() == null || state.getBlock().getRegistryName() == null) return "";
        var id = state.getBlock().getRegistryName();
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
    }

    static ResourceKey<net.minecraft.world.level.biome.Biome> analogFor(Climate c) {
        Set<Type> tags = c.tags();
        boolean wooded = tags.contains(Type.FOREST) || tags.contains(Type.CONIFEROUS) || tags.contains(Type.JUNGLE) || c.trees() >= 2;
        boolean sandy = tags.contains(Type.SANDY) || tags.contains(Type.DRY) || c.top().equals("sand");
        if (tags.contains(Type.NETHER) || tags.contains(Type.END) || tags.contains(Type.VOID)
                || tags.contains(Type.OCEAN) || tags.contains(Type.RIVER) || tags.contains(Type.WATER) || tags.contains(Type.BEACH)
                || c.baseHeight() < -0.5F) return null;
        if (tags.contains(Type.MUSHROOM)) return Biomes.MUSHROOM_FIELDS;
        if (tags.contains(Type.MESA) || tags.contains(Type.WASTELAND) || c.filler().equals("hardened_clay")) {
            return wooded || c.trees() > 0 ? Biomes.WOODED_BADLANDS : Biomes.BADLANDS;
        }
        if (tags.contains(Type.SNOWY) || c.temperature() < 0.15F || c.snow()
                || (c.lists().contains(BiomeManager.BiomeType.ICY) && c.lists().size() == 1)) {
            if (tags.contains(Type.MOUNTAIN)) return Biomes.GROVE;
            return wooded ? Biomes.SNOWY_TAIGA : Biomes.SNOWY_PLAINS;
        }
        if (tags.contains(Type.JUNGLE) || (c.temperature() >= 0.95F && c.rainfall() >= 0.8F)) return Biomes.JUNGLE;
        if (tags.contains(Type.SWAMP) || (c.rainfall() >= 0.85F && c.temperature() >= 0.5F && c.baseHeight() <= 0.0F)) return Biomes.SWAMP;
        if (tags.contains(Type.SAVANNA)) return Biomes.SAVANNA;
        if ((sandy && (tags.contains(Type.HOT) || tags.contains(Type.DRY))) || (c.temperature() >= 1.0F && c.rainfall() <= 0.2F)) {
            return c.top().equals("sand") || tags.contains(Type.SANDY) ? Biomes.DESERT : Biomes.SAVANNA;
        }
        if (tags.contains(Type.MOUNTAIN) || c.baseHeight() >= 1.0F) return wooded ? Biomes.WINDSWEPT_FOREST : Biomes.WINDSWEPT_HILLS;
        if (tags.contains(Type.CONIFEROUS) || (wooded && (tags.contains(Type.COLD) || c.temperature() <= 0.3F))) {
            return tags.contains(Type.DENSE) ? Biomes.OLD_GROWTH_SPRUCE_TAIGA : Biomes.TAIGA;
        }
        if (tags.contains(Type.SPOOKY) || (tags.contains(Type.DENSE) && wooded)) return Biomes.DARK_FOREST;
        if (wooded) return Biomes.FOREST;
        if (c.lists().contains(BiomeManager.BiomeType.DESERT) && c.lists().size() == 1) {
            return c.temperature() >= 1.0F ? Biomes.SAVANNA : Biomes.PLAINS;
        }
        if (c.temperature() <= 0.3F || (c.lists().contains(BiomeManager.BiomeType.COOL) && c.lists().size() == 1)) {
            return c.trees() > 0 ? Biomes.TAIGA : Biomes.PLAINS;
        }
        return Biomes.PLAINS;
    }

    private static String describe(List<BiomePlacement.Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (BiomePlacement.Entry entry : entries) {
            if (!out.isEmpty()) out.append(", ");
            out.append(entry.biome().location()).append(" -> ").append(entry.analog().location().getPath()).append(' ').append(entry.weight());
        }
        return out.toString();
    }

    record Climate(Set<Type> tags, Set<BiomeManager.BiomeType> lists, float temperature, float rainfall, float baseHeight, float variation,
            boolean snow, int trees, String top, String filler) {}
}

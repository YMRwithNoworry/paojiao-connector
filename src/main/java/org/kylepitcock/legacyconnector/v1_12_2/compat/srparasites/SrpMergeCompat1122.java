package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import java.util.Random;
import legacyconnector.v1_12_2.net.minecraft.entity.Entity;
import legacyconnector.v1_12_2.net.minecraft.entity.EntityList;
import legacyconnector.v1_12_2.net.minecraft.entity.EntityLiving;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraft.util.math.BlockPos;
import legacyconnector.v1_12_2.net.minecraft.world.World;

/** Shared helpers for the removed SRP 1.12.2 merge entry point. */
public final class SrpMergeCompat1122 {
    private SrpMergeCompat1122() {}

    public static String select(String[] table, int times, boolean random) {
        Random rng = new Random();
        if (random) return selectFromRandom(table, rng);

        for (String entry : table) {
            if (entry == null) continue;
            String[] parts = entry.split(";");
            if (Integer.parseInt(parts[1]) == times) return parts[0];
        }
        return selectFromRandom(table, rng);
    }

    private static String selectFromRandom(String[] table, Random rng) {
        int start = rng.nextInt(table.length);
        for (int checked = 0; checked < table.length; checked++) {
            String entry = table[(start + checked) % table.length];
            if (entry != null) return entry.split(";")[0];
        }
        return null;
    }

    public static EntityLiving createMergedEntity(Entity source, World world, String entityId) {
        if (world.isRemote) return null;
        EntityLiving spawned = (EntityLiving) EntityList.createEntityByIDFromName(new ResourceLocation(entityId), world);
        if (spawned == null) return null;
        spawned.setLocationAndAngles(Entity.mcc$get$posX(source),
                Entity.mcc$get$posY(source) + Entity.mcc$get$height(source) / 2.0D + 0.5D,
                Entity.mcc$get$posZ(source), Entity.mcc$get$rotationYaw(source), Entity.mcc$get$rotationPitch(source));
        spawned.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(spawned)), null);
        return spawned;
    }
}

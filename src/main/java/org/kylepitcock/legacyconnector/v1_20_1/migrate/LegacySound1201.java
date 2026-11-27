package org.kylepitcock.legacyconnector.v1_20_1.migrate;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.SampledFloat;

/** Preserves the 1.20.1 String constructor for mods that extend Sound. */
public class LegacySound1201 extends Sound {
    protected LegacySound1201(String location, SampledFloat volume, SampledFloat pitch, int weight, Sound.Type type,
            boolean stream, boolean preload, int attenuationDistance) {
        super(ResourceLocation.parse(location), volume, pitch, weight, type, stream, preload, attenuationDistance);
    }
}

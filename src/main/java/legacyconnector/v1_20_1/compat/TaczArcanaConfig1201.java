package legacyconnector.v1_20_1.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Arcana's gun-pack hook runs before the normal client configs are loaded. */
public final class TaczArcanaConfig1201 {
    private TaczArcanaConfig1201() {}

    public static void load(ModConfigSpec spec) {
        if (spec.isLoaded()) return;
        ModList.get().getModContainerById("taczexpands").orElseThrow()
                .registerConfig(ModConfig.Type.STARTUP, spec, "taczexpands-pre.toml");
    }
}

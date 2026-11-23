package legacyconnector.v1_20_1.net.minecraftforge.fml.javafmlmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/**
 * Forge 1.20.1's way for a mod constructor to reach its mod event bus ({@code FMLJavaModLoadingContext.get().getModEventBus()}). NeoForge 1.21.1 injects the
 * bus into the constructor instead; this reads it from the container FML is constructing the mod for.
 */
public class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();

    private FMLJavaModLoadingContext() {
    }

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        return ModLoadingContext.get().getActiveContainer().getEventBus();
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec) {
        getContainer().registerConfig(type, spec);
    }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) {
        getContainer().registerConfig(type, spec, fileName);
    }

    public <T extends net.neoforged.fml.IExtensionPoint> void registerExtensionPoint(Class<T> extension,
            java.util.function.Supplier<T> factory) {
        getContainer().registerExtensionPoint(extension, factory);
    }

    public ModContainer getContainer() {
        return ModLoadingContext.get().getActiveContainer();
    }
}

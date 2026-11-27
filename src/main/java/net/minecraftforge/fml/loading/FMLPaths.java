package net.minecraftforge.fml.loading;

import java.nio.file.Path;
import cpw.mods.modlauncher.api.IEnvironment;

/** Forge binary name for native callers; NeoForge remains the owner of the launcher paths. */
public enum FMLPaths {
    GAMEDIR(net.neoforged.fml.loading.FMLPaths.GAMEDIR),
    MODSDIR(net.neoforged.fml.loading.FMLPaths.MODSDIR),
    CONFIGDIR(net.neoforged.fml.loading.FMLPaths.CONFIGDIR),
    FMLCONFIG(net.neoforged.fml.loading.FMLPaths.FMLCONFIG);

    private final net.neoforged.fml.loading.FMLPaths path;

    FMLPaths(net.neoforged.fml.loading.FMLPaths path) {
        this.path = path;
    }

    public static void setup(IEnvironment environment) {
        net.neoforged.fml.loading.FMLPaths.setup(environment);
    }

    public static void loadAbsolutePaths(Path gameDir) {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(gameDir);
    }

    public static Path getOrCreateGameRelativePath(Path path) {
        return net.neoforged.fml.loading.FMLPaths.getOrCreateGameRelativePath(path);
    }

    public Path relative() { return path.relative(); }

    public Path get() { return path.get(); }
}

package org.kylepitcock.legacyconnector.v1_12_2.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.ModuleLayer;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreModPlugins1122Test {
    @Test
    void parsesOnlyTheCoremodManifestForThisLegacyVersion() {
        assertEquals("com.srpcotesia.SRPCotesiaFMLPlugin",
                CoreModPlugins1122.coremodClass("com.srpcotesia.SRPCotesiaFMLPlugin\n1.12.2\n"));
        assertNull(CoreModPlugins1122.coremodClass("com.example.LegacyPlugin\n1.7.10"));
        assertNull(CoreModPlugins1122.coremodClass("com.example.LegacyPlugin"));
    }

    @Test
    void loadsPluginThroughOwningModuleLoader() throws Exception {
        ClassLoader ownerLoader = getClass().getClassLoader();
        ClassLoader modLauncherLoader = new ClassLoader(ownerLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(PluginFixture.class.getName())) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };

        List<ClassLoader> candidates = CoreModPlugins1122.withOwnerLoaders(modLauncherLoader, List.of(ownerLoader));

        assertEquals(2, candidates.size());
        assertSame(modLauncherLoader, candidates.getFirst());
        assertSame(ownerLoader, candidates.get(1));
        assertSame(PluginFixture.class, CoreModPlugins1122.loadFrom(PluginFixture.class.getName(), candidates));
    }

    @Test
    void readsOwningModuleFromContainerAndFindsItsPackage() {
        Module owner = ModuleLayer.boot().findModule("java.sql").orElseThrow();
        ContainerFixture container = new ContainerFixture(owner);

        assertSame(owner, CoreModPlugins1122.owningModule(container));
        assertSame(owner.getClassLoader(),
                CoreModPlugins1122.moduleClassLoaderForPackage("java.sql.Connection", List.of(ModuleLayer.boot())));
    }

    private static final class ContainerFixture {
        private final Module layer;

        private ContainerFixture(Module layer) {
            this.layer = layer;
        }
    }

    private static final class PluginFixture {
    }
}

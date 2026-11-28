package org.kylepitcock.legacyconnector.loader.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyJarTransformerPlatformLibraryTest {
    @Test
    void dropsModuleProvidedMixinPackagesAndTheirServiceProviders() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("org/spongepowered/tools/obfuscation/fg3/SRPProcessor", new byte[0]);
        classes.put("org/spongepowered/asm/mixin/Mixin", new byte[0]);
        classes.put("com/llamalad7/mixinextras/injector/wrapoperation/Operation", new byte[0]);
        classes.put("com/example/mod/OwnedClass", new byte[0]);

        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("META-INF/services/org.spongepowered.tools.obfuscation.service.IOptions", new byte[0]);
        resources.put("META-INF/services/cpw.mods.modlauncher.api.INameMappingService", new byte[0]);
        resources.put("META-INF/services/example.ModService", new byte[0]);
        resources.put("mixins.srparasites.json", new byte[0]);

        assertEquals(5, LegacyJarTransformer.dropPlatformLibraries(classes, resources));
        assertFalse(classes.containsKey("org/spongepowered/tools/obfuscation/fg3/SRPProcessor"));
        assertFalse(classes.containsKey("org/spongepowered/asm/mixin/Mixin"));
        assertFalse(classes.containsKey("com/llamalad7/mixinextras/injector/wrapoperation/Operation"));
        assertTrue(classes.containsKey("com/example/mod/OwnedClass"));
        assertFalse(resources.containsKey("META-INF/services/org.spongepowered.tools.obfuscation.service.IOptions"));
        assertFalse(resources.containsKey("META-INF/services/cpw.mods.modlauncher.api.INameMappingService"));
        assertTrue(resources.containsKey("META-INF/services/example.ModService"));
        assertTrue(resources.containsKey("mixins.srparasites.json"));
    }

    @Test
    void platformLibrariesAreKeptOutOfCrossVersionRelocation() {
        assertTrue(LegacyJarTransformer.isProvidedLibrary("com/llamalad7/mixinextras/injector/wrapoperation/Operation"));
        assertTrue(LegacyJarTransformer.isProvidedLibrary("org/spongepowered/asm/mixin/Mixin"));
    }
}

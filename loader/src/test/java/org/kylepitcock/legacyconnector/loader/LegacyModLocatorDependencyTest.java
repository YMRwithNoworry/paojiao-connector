package org.kylepitcock.legacyconnector.loader;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyModLocatorDependencyTest {
    @Test
    void skipsSwparasitesBeforeConstructionWhenSpartanWeaponryIsAbsent() {
        LegacyJar swparasites = jar("swparasites");

        String reason = LegacyModLocator.missingHardDependency(swparasites, List.of(swparasites));

        assertTrue(reason.contains("spartanweaponry"));
    }

    @Test
    void keepsSwparasitesEligibleWhenSpartanWeaponryIsInstalled() {
        LegacyJar swparasites = jar("swparasites");
        LegacyJar spartan = jar("spartanweaponry");

        assertNull(LegacyModLocator.missingHardDependency(swparasites, List.of(swparasites, spartan)));
    }

    @Test
    void skipsCotesiaBeforeConstructionWhenGeckoLib3IsAbsent() {
        LegacyJar cotesia = jar("srpcotesia");

        String reason = LegacyModLocator.missingHardDependency(cotesia, List.of(cotesia));

        assertTrue(reason.contains("geckolib3"));
    }

    @Test
    void keepsCotesiaEligibleWhenGeckoLib3IsInstalled() {
        LegacyJar cotesia = jar("srpcotesia");
        LegacyJar geckolib = jar("geckolib3");

        assertNull(LegacyModLocator.missingHardDependency(cotesia, List.of(cotesia, geckolib)));
    }

    private static LegacyJar jar(String id) {
        LegacyJar jar = new LegacyJar(Path.of(id + ".jar"));
        jar.version = LegacyVersion.V1_12_2;
        LegacyModDecl mod = new LegacyModDecl();
        mod.legacyId = id;
        mod.version = "1.0";
        jar.mods.add(mod);
        return jar;
    }
}

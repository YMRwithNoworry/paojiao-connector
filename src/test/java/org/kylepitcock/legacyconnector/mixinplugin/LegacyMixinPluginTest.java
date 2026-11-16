package org.kylepitcock.legacyconnector.mixinplugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LegacyMixinPluginTest {
    @Test
    void permitsLegacyConfigsOnTranslatedClassesAndThe1122ApiShimOnly() {
        assertTrue(LegacyMixinPlugin.canApplyLegacyMixin("com/example/TranslatedEntity", true));
        assertTrue(LegacyMixinPlugin.canApplyLegacyMixin(
                "legacyconnector/v1_12_2/net/minecraft/entity/player/EntityPlayer", false));
        assertFalse(LegacyMixinPlugin.canApplyLegacyMixin(
                "legacyconnector/v1_7_10/net/minecraft/entity/player/EntityPlayer", false));
        assertFalse(LegacyMixinPlugin.canApplyLegacyMixin("net/minecraft/world/entity/player/Player", false));
    }
}

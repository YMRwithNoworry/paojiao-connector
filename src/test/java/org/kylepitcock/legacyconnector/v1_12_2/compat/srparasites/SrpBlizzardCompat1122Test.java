package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SrpBlizzardCompat1122Test {
    @Test
    void onlyReplacesVanillaWeatherAboveTheLegacyThreshold() {
        assertFalse(SrpBlizzardCompat1122.shouldRender(0.001F));
        assertTrue(SrpBlizzardCompat1122.shouldRender(0.0011F));
    }

    @Test
    void preservesDimensionWeatherAndReplacesOnlyVanillaPrecipitation() {
        assertTrue(SrpBlizzardCompat1122.shouldSkipVanillaWeather(true, false));
        assertFalse(SrpBlizzardCompat1122.shouldSkipVanillaWeather(false, false));
        assertTrue(SrpBlizzardCompat1122.shouldSkipVanillaWeather(false, true));
    }

    @Test
    void computesTheLegacyLinearFogPlanes() {
        var planes = SrpBlizzardCompat1122.fogPlanes(0.5F, false);

        assertEquals(44.0F, planes.far(), 0.0001F);
        assertEquals(3.52F, planes.near(), 0.0001F);
    }

    @Test
    void preservesVanillaFogWhenInactiveOrSubmerged() {
        assertNull(SrpBlizzardCompat1122.fogPlanes(0.001F, false));
        assertNull(SrpBlizzardCompat1122.fogPlanes(0.8F, true));
    }
}

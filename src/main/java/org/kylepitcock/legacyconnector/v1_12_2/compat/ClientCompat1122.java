package org.kylepitcock.legacyconnector.v1_12_2.compat;

import org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites.SrpBlizzardCompat1122;

/** Client setup for version-specific legacy mod integrations. */
public final class ClientCompat1122 {
    private ClientCompat1122() {}

    public static void register() {
        SrpBlizzardCompat1122.register();
    }
}

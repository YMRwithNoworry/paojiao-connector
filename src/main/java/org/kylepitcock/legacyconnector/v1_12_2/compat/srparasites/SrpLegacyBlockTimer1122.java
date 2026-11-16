package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

/** Restores the old SRP stage-specific block-infestation tick delay used by SRPExtra. */
public final class SrpLegacyBlockTimer1122 {
    private SrpLegacyBlockTimer1122() {}

    public static int getTimer(int stage, int stageOneSeconds, int stageTwoSeconds, int stageThreeSeconds) {
        return switch (stage) {
            case 1 -> stageOneSeconds * 20;
            case 2 -> stageTwoSeconds * 20;
            case 3 -> stageThreeSeconds * 20;
            default -> 40;
        };
    }
}

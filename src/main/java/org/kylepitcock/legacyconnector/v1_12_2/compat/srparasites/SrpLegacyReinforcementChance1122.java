package org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites;

/** Phase-indexed reinforcement chance values used by the legacy Cotesia Nexus interaction. */
public final class SrpLegacyReinforcementChance1122 {
    private SrpLegacyReinforcementChance1122() {}

    public static double getChance(byte phase, double one, double two, double three, double four, double five,
            double six, double seven, double eight, double nine, double ten) {
        return switch (phase) {
            case 1 -> one;
            case 2 -> two;
            case 3 -> three;
            case 4 -> four;
            case 5 -> five;
            case 6 -> six;
            case 7 -> seven;
            case 8 -> eight;
            case 9 -> nine;
            case 10 -> ten;
            default -> 0.0;
        };
    }
}

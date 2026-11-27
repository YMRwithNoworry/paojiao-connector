package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.v1_20_1.migrate.MiddleCompat;

class MiddleCompatScoreboardTest {
    @Test
    void objectiveLookupAndCreationPreserveLegacySemantics() {
        Scoreboard scoreboard = new Scoreboard();

        assertFalse(MiddleCompat.hasObjective(scoreboard, "arcana_test"));

        Objective created = MiddleCompat.getOrCreateObjective(scoreboard, "arcana_test");
        assertTrue(MiddleCompat.hasObjective(scoreboard, "arcana_test"));
        assertSame(created, MiddleCompat.getOrCreateObjective(scoreboard, "arcana_test"));
    }
}

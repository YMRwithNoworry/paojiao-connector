package legacyconnector.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class ForgeConfigValue1201Test {
    @Test
    void readsTheDeclaredDefaultBeforeTheConfigLoads() {
        var builder = new ModConfigSpec.Builder();
        var enabled = builder.define("enabled", false);
        var spec = builder.build();

        assertEquals(false, spec.isLoaded());
        assertThrows(IllegalStateException.class, enabled::get);
        assertEquals(false, ForgeApi1201.configValueGet(enabled));
    }
}

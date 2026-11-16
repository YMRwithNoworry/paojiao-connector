package legacyconnector.v1_12_2.net.minecraft.client.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimpleReloadableResourceManager1122Test {
    @Test
    void listenerRegisteredDuringReloadWaitsUntilModelsHaveBaked() {
        SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager();
        AtomicInteger calls = new AtomicInteger();

        manager.mcc$reloading();
        manager.registerReloadListener(ignored -> calls.incrementAndGet());

        assertEquals(0, calls.get());
    }
}

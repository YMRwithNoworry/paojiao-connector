package org.kylepitcock.legacyconnector.v1_12_2.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.Test;

class Registries1122Test {
    @Test
    void valuesSnapshotIsCachedImmutableAndStableAcrossRegistryChanges() {
        var registry = new Registries1122.Reg<>(TestEntry.class, entry -> {});
        TestEntry firstEntry = entry("test:first");
        TestEntry secondEntry = entry("test:second");

        List<TestEntry> empty = registry.getValues();
        assertSame(empty, registry.getValues());
        assertThrows(UnsupportedOperationException.class, () -> empty.add(firstEntry));

        registry.register(firstEntry);
        List<TestEntry> afterFirst = registry.getValues();
        assertNotSame(empty, afterFirst);
        assertEquals(List.of(firstEntry), afterFirst);
        assertSame(afterFirst, registry.getValues());
        registry.register(entry("test:first"));
        assertSame(afterFirst, registry.getValues());

        registry.register(secondEntry);
        List<TestEntry> afterSecond = registry.getValues();
        assertNotSame(afterFirst, afterSecond);
        assertEquals(List.of(firstEntry, secondEntry), afterSecond);
        assertEquals(List.of(firstEntry), afterFirst);

        assertSame(firstEntry, registry.remove(new ResourceLocation("test:first")));
        List<TestEntry> afterRemoval = registry.getValues();
        assertNotSame(afterSecond, afterRemoval);
        assertEquals(List.of(secondEntry), afterRemoval);
        assertEquals(List.of(firstEntry, secondEntry), afterSecond);
    }

    @Test
    void vanillaRegistrationAndAddCallbacksObserveCurrentSnapshot() {
        var registry = new Registries1122.Reg<>(TestEntry.class, entry -> {});
        TestEntry vanillaEntry = entry("test:vanilla");
        registry.vanilla = current -> current.registerVanilla(vanillaEntry);

        List<TestEntry> before = registry.getValues();
        assertEquals(List.of(vanillaEntry), before);
        assertSame(before, registry.getValues());

        TestEntry laterVanillaEntry = entry("test:later_vanilla");
        registry.registerVanilla(laterVanillaEntry);
        List<TestEntry> afterVanillaRegistration = registry.getValues();
        assertNotSame(before, afterVanillaRegistration);
        assertEquals(List.of(vanillaEntry, laterVanillaEntry), afterVanillaRegistration);
        assertEquals(List.of(vanillaEntry), before);

        AtomicReference<List<TestEntry>> callbackValues = new AtomicReference<>();
        registry.mcc$addCallback((owner, stage, id, value, oldValue) -> callbackValues.set(owner.getValues()));
        TestEntry registeredEntry = entry("test:registered");
        registry.register(registeredEntry);

        assertEquals(List.of(vanillaEntry, laterVanillaEntry, registeredEntry), callbackValues.get());
        assertSame(registry.getValues(), callbackValues.get());
    }

    private static TestEntry entry(String name) {
        return new TestEntry().setRegistryName(new ResourceLocation(name));
    }

    private static final class TestEntry extends IForgeRegistryEntry.Impl<TestEntry> {}
}

package legacyconnector.v1_12_2.net.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraftforge.fml.common.registry.EntityEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.v1_12_2.bridge.Registries1122;

class EntityList1122Test {
    @BeforeAll
    static void bootstrapRegistries() {
        net.neoforged.fml.loading.LoadingModList.of(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void classLookupsCacheTheFirstModEntryAndRefreshAfterRegistryChanges() {
        var registry = Registries1122.ENTITIES;
        ResourceLocation firstName = new ResourceLocation("test", "probe");
        ResourceLocation secondName = new ResourceLocation("test", "probe_second");
        EntityEntry first = entry(firstName);
        EntityEntry second = entry(secondName);

        try {
            assertEquals(-1, EntityList.getID(null));
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));

            registry.registerVanilla(first);
            assertSame(firstName, EntityList.getKey(ProbeEntity.class));
            assertSame(firstName, EntityList.getKey(ProbeEntity.class));
            assertEquals(registry.getID(first), EntityList.getID(ProbeEntity.class));

            registry.registerVanilla(second);
            assertSame(firstName, EntityList.getKey(ProbeEntity.class));

            registry.remove(firstName);
            assertSame(secondName, EntityList.getKey(ProbeEntity.class));
            assertEquals(registry.getID(second), EntityList.getID(ProbeEntity.class));

            registry.remove(secondName);
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));
        } finally {
            registry.remove(firstName);
            registry.remove(secondName);
        }
    }

    private static EntityEntry entry(ResourceLocation name) {
        EntityEntry entry = new EntityEntry(ProbeEntity.class, name.getPath());
        entry.setRegistryName(name);
        return entry;
    }

    private static final class ProbeEntity extends Entity {
        ProbeEntity() {
            super((legacyconnector.v1_12_2.net.minecraft.world.World) null);
        }
    }
}

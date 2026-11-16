package legacyconnector.v1_12_2.net.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void fallbackNamesPreserveFormattingAndReturnFreshLocations() {
        ResourceLocation first = EntityList.getKey(ProbeEntity.class);
        ResourceLocation second = EntityList.getKey(ProbeEntity.class);
        assertEquals(new ResourceLocation("minecraft", "probe_entity"), first);
        assertEquals(first, second);
        assertNotSame(first, second);
        assertEquals(new ResourceLocation("minecraft", "big_xml2_fish"), EntityList.getKey(EntityBigXML2Fish.class));
        assertNull(EntityList.getKey(Entity.class));
    }

    @Test
    void overriddenEntityClassIsReadOnEveryLookup() {
        var registry = Registries1122.ENTITIES;
        ResourceLocation name = new ResourceLocation("test", "mutable_entity");
        MutableEntityEntry entry = new MutableEntityEntry(ProbeEntity.class);
        entry.setRegistryName(name);
        try {
            registry.registerVanilla(entry);
            assertSame(name, EntityList.getKey(ProbeEntity.class));
            assertEquals(new ResourceLocation("minecraft", "big_xml2_fish"), EntityList.getKey(EntityBigXML2Fish.class));

            entry.entityClass = EntityBigXML2Fish.class;
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));
            assertSame(name, EntityList.getKey(EntityBigXML2Fish.class));
            assertEquals(-1, EntityList.getID(ProbeEntity.class));
            assertEquals(registry.getID(entry), EntityList.getID(EntityBigXML2Fish.class));

            entry.entityClass = ProbeEntity.class;
            assertSame(name, EntityList.getKey(ProbeEntity.class));
            assertEquals(new ResourceLocation("minecraft", "big_xml2_fish"), EntityList.getKey(EntityBigXML2Fish.class));
        } finally {
            registry.remove(name);
        }
    }

    @Test
    void unnamedEntryCanBeNamedAfterRegistration() {
        var registry = Registries1122.ENTITIES;
        ResourceLocation name = new ResourceLocation("test", "late_name");
        EntityEntry entry = new EntityEntry(ProbeEntity.class, "late_name");
        try {
            registry.registerVanilla(entry);
            assertThrows(NullPointerException.class, () -> EntityList.getKey(ProbeEntity.class));
            entry.setRegistryName(name);
            assertSame(name, EntityList.getKey(ProbeEntity.class));
        } finally {
            registry.remove(null);
        }
    }

    @Test
    void vanillaOverridesKeepTheirCallOrderAfterRegistryChanges() {
        var registry = Registries1122.ENTITIES;
        ResourceLocation modName = new ResourceLocation("test", "first_probe");
        ResourceLocation vanillaName = new ResourceLocation("minecraft", "dynamic_probe");
        EntityEntry first = entry(modName);
        MutableEntityEntry dynamic = new MutableEntityEntry(ProbeEntity.class);
        dynamic.setRegistryName(vanillaName);
        try {
            registry.registerVanilla(first);
            assertSame(modName, EntityList.getKey(ProbeEntity.class));
            assertSame(modName, EntityList.getKey(ProbeEntity.class));

            registry.registerVanilla(dynamic);
            dynamic.accessCount = 0;
            assertSame(modName, EntityList.getKey(ProbeEntity.class));
            assertSame(modName, EntityList.getKey(ProbeEntity.class));
            assertEquals(0, dynamic.accessCount);

            registry.remove(modName);
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));
            assertEquals(2, dynamic.accessCount);

            dynamic.entityClass = EntityBigXML2Fish.class;
            assertEquals(new ResourceLocation("minecraft", "probe_entity"), EntityList.getKey(ProbeEntity.class));
            assertEquals(new ResourceLocation("minecraft", "big_xml2_fish"), EntityList.getKey(EntityBigXML2Fish.class));
            assertEquals(4, dynamic.accessCount);
        } finally {
            registry.remove(modName);
            registry.remove(vanillaName);
        }
    }

    @Test
    void nullClassCanMatchAnEntryWithoutBeingCached() {
        var registry = Registries1122.ENTITIES;
        ResourceLocation name = new ResourceLocation("test", "null_class");
        EntityEntry entry = new EntityEntry(null, "null_class");
        entry.setRegistryName(name);
        try {
            registry.registerVanilla(entry);
            assertSame(name, EntityList.getKey((Class<? extends Entity>) null));
            assertEquals(registry.getID(entry), EntityList.getID(null));
        } finally {
            registry.remove(name);
        }
        assertEquals(-1, EntityList.getID(null));
        assertThrows(NullPointerException.class, () -> EntityList.getKey((Class<? extends Entity>) null));
    }

    private static final class MutableEntityEntry extends EntityEntry {
        private Class<? extends Entity> entityClass;
        private int accessCount;

        MutableEntityEntry(Class<? extends Entity> entityClass) {
            super(entityClass, "mutable_entity");
            this.entityClass = entityClass;
        }

        @Override
        public Class<? extends Entity> getEntityClass() {
            accessCount++;
            return entityClass;
        }
    }

    private static EntityEntry entry(ResourceLocation name) {
        EntityEntry entry = new EntityEntry(ProbeEntity.class, name.getPath());
        entry.setRegistryName(name);
        return entry;
    }

    private static final class EntityBigXML2Fish extends Entity {
        EntityBigXML2Fish() {
            super((legacyconnector.v1_12_2.net.minecraft.world.World) null);
        }
    }

    private static final class ProbeEntity extends Entity {
        ProbeEntity() {
            super((legacyconnector.v1_12_2.net.minecraft.world.World) null);
        }
    }
}

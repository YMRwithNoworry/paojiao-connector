package legacyconnector.v1_12_2.net.minecraftforge.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import legacyconnector.v1_12_2.net.minecraft.client.resources.IResourceManager;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.kylepitcock.legacyconnector.v1_12_2.bridge.ClientRegistrations1122;

class ModelLoaderVanillaBypass1122Test {
    @Test
    void vanillaLoaderBypassesCustomLoadersForTheirOwnFallbackModels() throws Exception {
        String namespace = "legacyconnector_vanilla_bypass_test";
        ResourceLocation location = new ResourceLocation(namespace, "probe");
        IModel customModel = new IModel() {};
        AtomicInteger customLoads = new AtomicInteger();
        ClientRegistrations1122.addModelLoader(new ICustomModelLoader() {
            @Override
            public boolean accepts(ResourceLocation candidate) {
                return namespace.equals(candidate.getNamespace());
            }

            @Override
            public IModel loadModel(ResourceLocation candidate) {
                customLoads.incrementAndGet();
                return customModel;
            }

            @Override
            public void onResourceManagerReload(IResourceManager resourceManager) {
            }
        });

        IModel vanillaModel = ModelLoader.VanillaLoader.INSTANCE.loadModel(location);

        assertNotSame(customModel, vanillaModel);
        assertEquals(0, customLoads.get());
        assertTrue(ModelLoaderRegistry.mcc$requested().contains(location));
        assertEquals(namespace + ":item/probe", ModelLoaderRegistry.mcc$standaloneId(new ResourceLocation(namespace, "models/item/probe")).toString());
        assertEquals(namespace + ":item/probe", ModelLoaderRegistry.mcc$standaloneId(new ResourceLocation(namespace, "item/probe")).toString());
    }
}

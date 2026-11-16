package legacyconnector.v1_12_2.net.minecraft.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelShulkerBullet1122Test {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        var dist = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, net.neoforged.api.distmarker.Dist.CLIENT);
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void definesAllThreeOrthogonalBulletPlatesAndAppliesTheLegacyRotation() {
        ModelShulkerBullet model = new ModelShulkerBullet();
        assertEquals(1, model.boxList.size());
        ModelRenderer main = model.boxList.get(0);
        assertEquals(3, main.cubeList.size());
        assertBox(main.cubeList.get(0), -4, -4, -1, 4, 4, 1);
        assertBox(main.cubeList.get(1), -1, -4, -4, 1, 4, 4);
        assertBox(main.cubeList.get(2), -4, -1, -4, 4, 1, 4);

        model.setRotationAngles(0, 0, 0, 90, 45, 1.0F, null);

        assertEquals((float) Math.PI / 2.0F, main.rotateAngleY, 1.0E-6F);
        assertEquals((float) Math.PI / 4.0F, main.rotateAngleX, 1.0E-6F);
    }

    private static void assertBox(ModelBox box, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        assertEquals(minX, box.posX1);
        assertEquals(minY, box.posY1);
        assertEquals(minZ, box.posZ1);
        assertEquals(maxX, box.posX2);
        assertEquals(maxY, box.posY2);
        assertEquals(maxZ, box.posZ2);
    }
}

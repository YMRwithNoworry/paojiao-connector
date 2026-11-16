package legacyconnector.v1_12_2.net.minecraft.client.renderer.entity.layers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import legacyconnector.v1_12_2.net.minecraft.client.model.ModelBase;
import legacyconnector.v1_12_2.net.minecraft.init.SoundEvents;
import legacyconnector.v1_12_2.net.minecraft.inventory.EntityEquipmentSlot;
import legacyconnector.v1_12_2.net.minecraft.item.ItemArmor;
import legacyconnector.v1_12_2.net.minecraft.item.ItemStack;
import legacyconnector.v1_12_2.net.minecraft.util.ResourceLocation;
import legacyconnector.v1_12_2.net.minecraftforge.common.util.EnumHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArmorMaterialTexture1122Test {
    @BeforeAll
    static void bootstrap() throws Exception {
        var dist = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, net.neoforged.api.distmarker.Dist.CLIENT);
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void usesForgeTextureNameWithoutChangingEnumIdentity() {
        var material = EnumHelper.addArmorMaterial("legacy_test_armor", "examplemod:sentients", 10, new int[] {1, 2, 3, 1}, 12,
                SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.5F);
        var chest = new ItemArmor(material, 0, EntityEquipmentSlot.CHEST);
        var leggings = new ItemArmor(material, 0, EntityEquipmentSlot.LEGS);
        var layer = new TestArmorLayer();

        assertEquals("legacy_test_armor", material.name());
        assertEquals("examplemod:sentients", material.getName());
        assertEquals(new ResourceLocation("examplemod:textures/models/armor/sentients_layer_1.png"),
                layer.getArmorResource(null, new ItemStack(chest), EntityEquipmentSlot.CHEST, null));
        assertEquals(new ResourceLocation("examplemod:textures/models/armor/sentients_layer_2.png"),
                layer.getArmorResource(null, new ItemStack(leggings), EntityEquipmentSlot.LEGS, null));
    }

    private static final class TestArmorLayer extends LayerArmorBase<ModelBase> {
        private TestArmorLayer() {
            super(null);
        }

        @Override
        public void initArmor() {}

        @Override
        public void setModelSlotVisible(ModelBase model, EntityEquipmentSlot slotIn) {}
    }
}

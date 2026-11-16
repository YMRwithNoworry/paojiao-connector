package legacyconnector.v1_12_2.net.minecraft.client.model;

import legacyconnector.v1_12_2.net.minecraft.entity.Entity;

/** 1.12 shulker-bullet model: three orthogonal 8x8 plates, rendered with the texture bound by its entity renderer. */
public class ModelShulkerBullet extends ModelBase {
    private static final float DEGREES_TO_RADIANS = 0.017453292F;
    private final ModelRenderer main;

    public ModelShulkerBullet() {
        textureWidth = 64;
        textureHeight = 32;
        main = new ModelRenderer(this, "main");
        main.setTextureOffset(0, 0).addBox(-4.0F, -4.0F, -1.0F, 8, 8, 2);
        main.setTextureOffset(0, 10).addBox(-1.0F, -4.0F, -4.0F, 2, 8, 8);
        main.setTextureOffset(20, 0).addBox(-4.0F, -1.0F, -4.0F, 8, 2, 8);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        main.render(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor, Entity entity) {
        main.rotateAngleY = netHeadYaw * DEGREES_TO_RADIANS;
        main.rotateAngleX = headPitch * DEGREES_TO_RADIANS;
    }
}

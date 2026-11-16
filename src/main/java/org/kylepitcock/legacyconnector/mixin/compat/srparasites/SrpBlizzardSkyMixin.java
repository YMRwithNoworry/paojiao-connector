package org.kylepitcock.legacyconnector.mixin.compat.srparasites;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites.SrpBlizzardCompat1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class SrpBlizzardSkyMixin {
    @Redirect(
            method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private float[] mcc$hideBlizzardSunrise(DimensionSpecialEffects effects, float celestialAngle, float partialTick) {
        return SrpBlizzardCompat1122.sunriseColor(effects, celestialAngle, partialTick);
    }
}

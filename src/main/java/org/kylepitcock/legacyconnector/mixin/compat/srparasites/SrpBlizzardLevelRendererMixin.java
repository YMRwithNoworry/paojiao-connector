package org.kylepitcock.legacyconnector.mixin.compat.srparasites;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.kylepitcock.legacyconnector.v1_12_2.compat.srparasites.SrpBlizzardCompat1122;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class SrpBlizzardLevelRendererMixin {
    @Redirect(method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSnowAndRain(Lnet/minecraft/client/multiplayer/ClientLevel;IFLnet/minecraft/client/renderer/LightTexture;DDD)Z"))
    private boolean mcc$replaceVanillaWeather(DimensionSpecialEffects effects, ClientLevel level, int ticks, float partialTick,
            LightTexture lightTexture, double camX, double camY, double camZ) {
        return SrpBlizzardCompat1122.shouldSkipVanillaWeather(
                effects.renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ), SrpBlizzardCompat1122.isActive(partialTick));
    }
}

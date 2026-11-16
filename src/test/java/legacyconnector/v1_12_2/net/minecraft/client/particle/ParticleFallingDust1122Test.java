package legacyconnector.v1_12_2.net.minecraft.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import legacyconnector.v1_12_2.net.minecraft.client.renderer.BufferBuilder;
import legacyconnector.v1_12_2.net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.junit.jupiter.api.Test;

class ParticleFallingDust1122Test {
    @Test
    void initializesTheVanillaColorScaleAndLifetime() {
        ParticleFallingDust particle = new ParticleFallingDust(null, 3.0D, 5.0D, -2.0D, 0.2F, 0.5F, 0.9F);

        assertEquals(3.0D, particle.posX, 0.0D);
        assertEquals(5.0D, particle.posY, 0.0D);
        assertEquals(-2.0D, particle.posZ, 0.0D);
        assertEquals(0.0D, particle.motionX, 0.0D);
        assertEquals(0.0D, particle.motionY, 0.0D);
        assertEquals(0.0D, particle.motionZ, 0.0D);
        assertEquals(0.2F, particle.particleRed, 0.0F);
        assertEquals(0.5F, particle.particleGreen, 0.0F);
        assertEquals(0.9F, particle.particleBlue, 0.0F);
        assertEquals(particle.oSize, particle.particleScale, 0.0F);
        assertTrue(particle.particleScale > 0.0F && particle.particleScale < 2.0F);
        assertTrue(particle.particleMaxAge >= 28 && particle.particleMaxAge <= 143);
        assertTrue(particle.rotSpeed >= -0.05F && particle.rotSpeed < 0.05F);
        assertTrue(particle.particleAngle >= 0.0F && particle.particleAngle < (float) (Math.PI * 2.0D));
        assertEquals(0, particle.getFXLayer());
    }

    @Test
    void rotatesGrowsRendersAndFallsWithTheLegacyUpdateRules() {
        ParticleFallingDust particle = new ParticleFallingDust(null, 3.0D, 5.0D, -2.0D, 1.0F, 0.7F, 0.4F);
        float initialAngle = particle.particleAngle;
        float partialTicks = 0.5F;

        particle.onUpdate();

        assertEquals(1, particle.particleAge);
        assertEquals(7, particle.particleTextureIndexX);
        assertEquals(0, particle.particleTextureIndexY);
        assertEquals(initialAngle + (float) Math.PI * particle.rotSpeed * 2.0F, particle.particleAngle, 0.0F);
        assertEquals(-0.003000000026077032D, particle.motionY, 0.0D);
        assertEquals(5.0D, particle.posY, 0.0D);

        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        float expectedScale = particle.oSize * Math.max(0.0F, Math.min(1.0F,
                ((float) particle.particleAge + partialTicks) / (float) particle.particleMaxAge * 32.0F));
        particle.renderParticle(buffer, null, partialTicks, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        assertEquals(expectedScale, particle.particleScale, 0.0F);
        assertEquals(4, buffer.getVertexCount());

        for (int tick = 0; tick < 80; tick++) particle.onUpdate();
        assertTrue(particle.motionY >= -0.14000000059604645D);
        assertTrue(particle.motionY < -0.13D);
        assertTrue(particle.posY < 5.0D);

        for (int tick = 0; tick <= particle.particleMaxAge; tick++) particle.onUpdate();
        assertFalse(particle.isAlive());
    }
}

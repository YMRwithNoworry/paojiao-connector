package legacyconnector.v1_12_2.net.minecraft.client.particle;

import legacyconnector.v1_12_2.net.minecraft.client.renderer.BufferBuilder;
import legacyconnector.v1_12_2.net.minecraft.entity.Entity;
import legacyconnector.v1_12_2.net.minecraft.world.World;

/** 1.12.2's slowly falling, rotating dust particle on the particle sheet. */
public class ParticleFallingDust extends Particle {
    float oSize;
    final float rotSpeed;

    protected ParticleFallingDust(World worldIn, double xCoordIn, double yCoordIn, double zCoordIn,
            float red, float green, float blue) {
        super(worldIn, xCoordIn, yCoordIn, zCoordIn, 0.0D, 0.0D, 0.0D);
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
        particleRed = red;
        particleGreen = green;
        particleBlue = blue;
        particleScale *= 0.75F;
        particleScale *= 0.9F;
        oSize = particleScale;
        particleMaxAge = (int) (32.0D / (Math.random() * 0.8D + 0.2D));
        particleMaxAge = (int) ((float) particleMaxAge * 0.9F);
        rotSpeed = ((float) Math.random() - 0.5F) * 0.1F;
        particleAngle = (float) Math.random() * ((float) Math.PI * 2.0F);
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX,
            float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float scale = ((float) particleAge + partialTicks) / (float) particleMaxAge * 32.0F;
        scale = Math.max(0.0F, Math.min(1.0F, scale));
        particleScale = oSize * scale;
        super.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge) setExpired();
        prevParticleAngle = particleAngle;
        particleAngle += (float) Math.PI * rotSpeed * 2.0F;
        if (onGround) prevParticleAngle = particleAngle = 0.0F;
        setParticleTextureIndex(7 - particleAge * 8 / particleMaxAge);
        move(motionX, motionY, motionZ);
        motionY -= 0.003000000026077032D;
        motionY = Math.max(motionY, -0.14000000059604645D);
    }
}

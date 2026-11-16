package legacyconnector.v1_12_2.net.minecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyEntityYawVelocity1122Test {
    @Test
    void exposesTheLegacyLivingBaseFieldToPlayersAndAllLivingSubclasses() throws Exception {
        var field = EntityLivingBase.class.getField("randomYawVelocity");

        assertEquals(float.class, field.getType());
        assertEquals(EntityLivingBase.class, field.getDeclaringClass());
        assertEquals(field, EntityLiving.class.getField("randomYawVelocity"));
        assertEquals(field, legacyconnector.v1_12_2.net.minecraft.entity.player.EntityPlayer.class
                .getField("randomYawVelocity"));
    }
}

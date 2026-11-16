package legacyconnector.v1_12_2.net.minecraft.network.play.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import legacyconnector.v1_12_2.net.minecraft.entity.Entity;
import legacyconnector.v1_12_2.net.minecraft.network.PacketBuffer;
import legacyconnector.v1_12_2.net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SPacketCamera1122Test {
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
    void translatesTheLegacyCameraPacketToTheModernPacketForItsEntity() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var pig = (net.minecraft.world.entity.animal.Pig) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(net.minecraft.world.entity.animal.Pig.class);
        pig.setId(73);
        Entity legacyView = new Entity((World) null) {};
        legacyView.mcc$entity = pig;

        var modern = new SPacketCamera(legacyView).mcc$modern();

        assertInstanceOf(net.minecraft.network.protocol.game.ClientboundSetCameraPacket.class, modern);
        Field cameraId = modern.getClass().getDeclaredField("cameraId");
        cameraId.setAccessible(true);
        assertEquals(73, cameraId.getInt(modern));
    }

    @Test
    void retainsTheLegacyWireEntityId() throws Exception {
        Entity legacyView = new Entity((World) null) {};
        legacyView.setEntityId(37);
        PacketBuffer outgoing = new PacketBuffer(Unpooled.buffer());
        new SPacketCamera(legacyView).writePacketData(outgoing);
        SPacketCamera incoming = new SPacketCamera();

        incoming.readPacketData(outgoing);

        assertEquals(37, incoming.getCameraEntityId());
        outgoing.release();
    }
}

package legacyconnector.v1_12_2.net.minecraft.network.play.server;

import java.io.IOException;
import legacyconnector.v1_12_2.net.minecraft.entity.Entity;
import legacyconnector.v1_12_2.net.minecraft.network.Packet;
import legacyconnector.v1_12_2.net.minecraft.network.PacketBuffer;
import legacyconnector.v1_12_2.net.minecraft.network.play.INetHandlerPlayClient;

/** Changes the client's camera to the selected entity. */
public class SPacketCamera implements Packet<INetHandlerPlayClient> {
    private Entity cameraEntity;
    private int cameraEntityId;

    public SPacketCamera() {
    }

    public SPacketCamera(Entity cameraEntity) {
        this.cameraEntity = cameraEntity;
        this.cameraEntityId = cameraEntity == null ? -1 : cameraEntity.getEntityId();
    }

    public int getCameraEntityId() {
        return cameraEntityId;
    }

    @Override
    public void readPacketData(PacketBuffer buf) throws IOException {
        cameraEntityId = buf.readVarInt();
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeVarInt(cameraEntityId);
    }

    @Override
    public void processPacket(INetHandlerPlayClient handler) {
    }

    @Override
    public net.minecraft.network.protocol.Packet<?> mcc$modern() {
        net.minecraft.world.entity.Entity modernCamera = cameraEntity == null ? null : cameraEntity.mcc$modern();
        return modernCamera == null ? null : new net.minecraft.network.protocol.game.ClientboundSetCameraPacket(modernCamera);
    }
}

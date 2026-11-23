package legacyconnector.v1_20_1.net.minecraftforge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;

/** Forge metadata and the native packet used to send a custom payload. */
public interface ICustomPacket<T extends Packet<?>> {
    @SuppressWarnings("unchecked")
    default T getThis() {
        return (T) this;
    }

    default FriendlyByteBuf getInternalData() {
        return null;
    }

    default int getIndex() {
        return 0;
    }

    default ResourceLocation getName() {
        return null;
    }
}

package net.minecraftforge.network;

import legacyconnector.v1_20_1.net.minecraftforge.network.event.EventNetworkChannel;
import legacyconnector.v1_20_1.net.minecraftforge.network.ICustomPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import org.apache.commons.lang3.tuple.Pair;

/** Forge 1.20.1's packet directions. */
public enum NetworkDirection {
    PLAY_TO_SERVER(LogicalSide.SERVER),
    PLAY_TO_CLIENT(LogicalSide.CLIENT),
    LOGIN_TO_SERVER(LogicalSide.SERVER),
    LOGIN_TO_CLIENT(LogicalSide.CLIENT);

    private final LogicalSide reception;

    NetworkDirection(LogicalSide reception) {
        this.reception = reception;
    }

    public LogicalSide getReceptionSide() {
        return reception;
    }

    public LogicalSide getOriginationSide() {
        return reception == LogicalSide.CLIENT ? LogicalSide.SERVER : LogicalSide.CLIENT;
    }

    public <T extends Packet<?>> ICustomPacket<T> buildPacket(Pair<FriendlyByteBuf, Integer> packetData, ResourceLocation channelName) {
        if (this == LOGIN_TO_SERVER || this == LOGIN_TO_CLIENT) {
            throw new UnsupportedOperationException("Forge login payloads are not supported: " + channelName);
        }
        var buf = packetData.getLeft();
        var bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        var payload = new EventNetworkChannel.Payload(new CustomPacketPayload.Type<>(channelName), bytes);
        Packet<?> packet = this == PLAY_TO_SERVER ? new ServerboundCustomPayloadPacket(payload) : new ClientboundCustomPayloadPacket(payload);
        @SuppressWarnings("unchecked")
        T typedPacket = (T) packet;
        ICustomPacket<T> result = new ICustomPacket<>() {
            @Override public T getThis() { return typedPacket; }
            @Override public ResourceLocation getName() { return channelName; }
            @Override public FriendlyByteBuf getInternalData() {
                return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
            }
        };
        // A successfully constructed packet owns the data; the native packet may be queued or broadcast.
        buf.release();
        return result;
    }

    public int getVanillaPacketType() {
        return ordinal() & 1;
    }

    public static NetworkDirection valueOf(int ordinal) {
        return values()[ordinal];
    }
}

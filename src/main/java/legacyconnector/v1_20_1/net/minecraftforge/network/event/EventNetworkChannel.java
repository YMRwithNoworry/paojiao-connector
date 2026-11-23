package legacyconnector.v1_20_1.net.minecraftforge.network.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import legacyconnector.v1_20_1.net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Forge play payloads, transported by NeoForge under the negotiated legacy channel ID. */
public class EventNetworkChannel {
    private static final List<EventNetworkChannel> CHANNELS = new ArrayList<>();
    private final IEventBus listeners = BusBuilder.builder().build();
    private final ResourceLocation name;
    private final String version;
    private final CustomPacketPayload.Type<Payload> type;

    public EventNetworkChannel(ResourceLocation name, String version) {
        this.name = name;
        this.version = version;
        this.type = new CustomPacketPayload.Type<>(name);
        synchronized (CHANNELS) {
            CHANNELS.add(this);
        }
    }

    public ResourceLocation getName() {
        return name;
    }

    public void registerObject(Object target) {
        listeners.register(target);
    }

    public void unregisterObject(Object target) {
        listeners.unregister(target);
    }

    public <T extends NetworkEvent> void addListener(Consumer<T> eventListener) {
        listeners.addListener(eventListener);
    }

    public boolean isRemotePresent(Connection manager) {
        return manager != null && NetworkRegistry.hasChannel(manager, ConnectionProtocol.PLAY, name);
    }

    public static void registerAll(RegisterPayloadHandlersEvent event) {
        synchronized (CHANNELS) {
            for (EventNetworkChannel channel : CHANNELS) {
                event.registrar(channel.version).optional().executesOn(HandlerThread.NETWORK).playBidirectional(channel.type,
                        StreamCodec.of((buf, payload) -> buf.writeBytes(payload.bytes), buf -> {
                            byte[] bytes = new byte[buf.readableBytes()];
                            buf.readBytes(bytes);
                            return new Payload(channel.type, bytes);
                        }), channel::handle);
            }
        }
    }

    private void handle(Payload payload, IPayloadContext delegate) {
        var context = new NetworkEvent.Context(delegate);
        var data = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.bytes));
        try {
            // Forge names these events for the side that originated the packet.
            listeners.post(delegate.flow().isServerbound() ? new NetworkEvent.ClientCustomPayloadEvent(context, data)
                    : new NetworkEvent.ServerCustomPayloadEvent(context, data));
        } finally {
            data.release();
        }
    }

    public record Payload(CustomPacketPayload.Type<Payload> type, byte[] bytes) implements CustomPacketPayload {}
}

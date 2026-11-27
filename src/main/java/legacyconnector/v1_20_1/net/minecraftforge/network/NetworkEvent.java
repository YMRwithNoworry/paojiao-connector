package legacyconnector.v1_20_1.net.minecraftforge.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkDirection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Forge 1.20.1's play payload event and message handling context. */
public class NetworkEvent extends Event {
    private final Context context;
    private final FriendlyByteBuf payload;

    public NetworkEvent(Context context) {
        this(context, null);
    }

    private NetworkEvent(Context context, FriendlyByteBuf payload) {
        this.context = context;
        this.payload = payload;
    }

    public FriendlyByteBuf getPayload() {
        return payload;
    }

    public Supplier<Context> getSource() {
        return () -> context;
    }

    /** A custom payload sent by the client. */
    public static class ClientCustomPayloadEvent extends NetworkEvent {
        public ClientCustomPayloadEvent(Context context) {
            super(context);
        }

        public ClientCustomPayloadEvent(Context context, FriendlyByteBuf payload) {
            super(context, payload);
        }
    }

    /** A custom payload sent by the server. */
    public static class ServerCustomPayloadEvent extends NetworkEvent {
        public ServerCustomPayloadEvent(Context context) {
            super(context);
        }

        public ServerCustomPayloadEvent(Context context, FriendlyByteBuf payload) {
            super(context, payload);
        }
    }

    public static class GatherLoginPayloadsEvent extends NetworkEvent {
        public GatherLoginPayloadsEvent(Context context) {
            super(context);
        }
    }

    public static class LoginPayloadEvent extends NetworkEvent {
        public LoginPayloadEvent(Context context) {
            super(context);
        }
    }

    /** What a message handler gets: the direction, the sending player, the main-thread queue. Backed by NeoForge's payload context. */
    public static class Context {
        private final IPayloadContext delegate;
        private final NetworkDirection direction;
        private boolean handled;

        public Context(IPayloadContext delegate) {
            this.delegate = delegate;
            this.direction = delegate.flow().isServerbound() ? NetworkDirection.PLAY_TO_SERVER : NetworkDirection.PLAY_TO_CLIENT;
        }

        public NetworkDirection getDirection() {
            return direction;
        }

        /** The player who sent a serverbound message; null for clientbound ones. */
        public ServerPlayer getSender() {
            return delegate.flow().isServerbound() && delegate.player() instanceof ServerPlayer p ? p : null;
        }

        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            return delegate.enqueueWork(runnable);
        }

        public <T> CompletableFuture<T> enqueueWork(Supplier<T> supplier) {
            return delegate.enqueueWork(supplier);
        }

        public void setPacketHandled(boolean handled) {
            this.handled = handled;
        }

        public boolean getPacketHandled() {
            return handled;
        }

        public net.minecraft.network.Connection getNetworkManager() {
            return delegate.connection();
        }

        /** The NeoForge context (not part of Forge's API). */
        public IPayloadContext mcc$delegate() {
            return delegate;
        }
    }
}

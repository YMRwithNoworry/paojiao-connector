package legacyconnector.v1_20_1.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;

/** Bridges Arcana's 1.20.1 play-state check to the 1.21.1 connection protocol API. */
public final class TaczArcanaClientCompat1201 {
    private TaczArcanaClientCompat1201() {}

    public static boolean isPlayClientConnection() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;

        Connection connection = listener.getConnection();
        if (connection == null || !connection.isConnected()) return false;

        ProtocolInfo<?> protocol = connection.getInboundProtocol();
        return protocol != null && protocol.id() == ConnectionProtocol.PLAY;
    }
}

package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public record ClientboundBridgeInfoPayload(
        int protocolVersion, String bridgeVersion, int capabilities, Set<String> onlinePlayers
) implements CustomPacketPayload {
    public static final int CAP_BANNED_WORDS = 1 << 0;
    public static final CustomPacketPayload.Type<ClientboundBridgeInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "bridge_info"));

    public static final StreamCodec<ByteBuf, ClientboundBridgeInfoPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundBridgeInfoPayload::write, ClientboundBridgeInfoPayload::read);

    private static void write(ByteBuf buf, ClientboundBridgeInfoPayload p) {
        buf.writeInt(p.protocolVersion);
        writeUtf(buf, p.bridgeVersion);
        buf.writeInt(p.capabilities);
        buf.writeInt(p.onlinePlayers.size());
        for (String uuid : p.onlinePlayers) {
            writeUtf(buf, uuid);
        }
    }

    private static ClientboundBridgeInfoPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        String bridgeVersion = PayloadLimits.readUtf(buf);
        int capabilities = buf.readInt();
        int onlineCount = PayloadLimits.readCount(buf, PayloadLimits.MAX_PLAYERS);
        Set<String> onlinePlayers = new HashSet<>(onlineCount);
        for (int i = 0; i < onlineCount; i++) {
            onlinePlayers.add(PayloadLimits.readUtf(buf));
        }
        return new ClientboundBridgeInfoPayload(protocolVersion, bridgeVersion, capabilities, onlinePlayers);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            cn.sarskin.ChatSphere.client.ChatHistoryManager history =
                    cn.sarskin.ChatSphere.client.ChatHistoryManager.getInstance();
            history.setBridgeInfo(this);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

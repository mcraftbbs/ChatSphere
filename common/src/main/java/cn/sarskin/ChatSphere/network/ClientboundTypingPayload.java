package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Someone is typing in a conversation the local player can see. Never stored, purely live. */
public record ClientboundTypingPayload(UUID playerUuid, String playerName, String conversationId,
                                       String conversationType) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundTypingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "typing_relay"));

    public static final StreamCodec<ByteBuf, ClientboundTypingPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundTypingPayload::write, ClientboundTypingPayload::read);

    private static void write(ByteBuf buf, ClientboundTypingPayload p) {
        buf.writeLong(p.playerUuid.getMostSignificantBits());
        buf.writeLong(p.playerUuid.getLeastSignificantBits());
        writeUtf(buf, p.playerName);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
    }

    private static ClientboundTypingPayload read(ByteBuf buf) {
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        return new ClientboundTypingPayload(uuid, PayloadLimits.readUtf(buf),
                PayloadLimits.readUtf(buf), PayloadLimits.readUtf(buf));
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

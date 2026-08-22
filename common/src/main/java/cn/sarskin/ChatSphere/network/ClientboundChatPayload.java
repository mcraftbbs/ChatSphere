package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.StoredMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ClientboundChatPayload(StoredMessage message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "chat"));

    public static final StreamCodec<ByteBuf, ClientboundChatPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChatPayload::write, ClientboundChatPayload::read);

    private static void write(ByteBuf buf, ClientboundChatPayload p) {
        StoredMessage m = p.message;
        writeUtf(buf, m.senderName());
        writeUuid(buf, m.senderUuid());
        writeUtf(buf, m.content());
        buf.writeLong(m.timestamp());
        writeUtf(buf, m.conversationId());
        writeUtf(buf, m.conversationType());
        writeUtf(buf, m.replyContent());
        writeUtf(buf, m.replySender());
        writeUtf(buf, m.itemNbt());
        writeUuid(buf, m.messageId());
        buf.writeBoolean(m.isInput());
    }

    private static ClientboundChatPayload read(ByteBuf buf) {
        String senderName = PayloadLimits.readUtf(buf);
        UUID senderUuid = readUuid(buf);
        String content = PayloadLimits.readUtf(buf);
        long timestamp = buf.readLong();
        String conversationId = PayloadLimits.readUtf(buf);
        String conversationType = PayloadLimits.readUtf(buf);
        String replyContent = PayloadLimits.readUtf(buf);
        String replySender = PayloadLimits.readUtf(buf);
        String itemNbt = PayloadLimits.readUtf(buf);
        UUID messageId = readUuid(buf);
        boolean isInput = buf.readBoolean();
        return new ClientboundChatPayload(new StoredMessage(senderName, senderUuid, content, timestamp, conversationId, conversationType, replyContent, replySender, itemNbt, messageId, isInput));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        if (uuid == null) uuid = Util.NIL_UUID;
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
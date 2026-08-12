package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClientboundMessageSyncPayload(List<StoredMessage> messages) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundMessageSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "message_sync"));

    public static final StreamCodec<ByteBuf, ClientboundMessageSyncPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundMessageSyncPayload::write, ClientboundMessageSyncPayload::read);

    private static void write(ByteBuf buf, ClientboundMessageSyncPayload p) {
        buf.writeInt(p.messages.size());
        for (StoredMessage m : p.messages) {
            writeUtf(buf, m.senderName());
            writeUuid(buf, m.senderUuid());
            writeUtf(buf, m.content());
            buf.writeLong(m.timestamp());
            writeUtf(buf, m.conversationId());
            writeUtf(buf, m.conversationType());
            writeUtf(buf, m.replyContent());
            writeUtf(buf, m.replySender());
            writeUtf(buf, m.itemNbt());
        }
    }

    private static ClientboundMessageSyncPayload read(ByteBuf buf) {
        int count = PayloadLimits.readCount(buf, PayloadLimits.MAX_MESSAGES);
        List<StoredMessage> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String senderName = PayloadLimits.readUtf(buf);
            UUID senderUuid = readUuid(buf);
            String content = PayloadLimits.readUtf(buf);
            long timestamp = buf.readLong();
            String conversationId = PayloadLimits.readUtf(buf);
            String conversationType = PayloadLimits.readUtf(buf);
            String replyContent = PayloadLimits.readUtf(buf);
            String replySender = PayloadLimits.readUtf(buf);
            String itemNbt = PayloadLimits.readUtf(buf);
            list.add(new StoredMessage(senderName, senderUuid, content, timestamp, conversationId, conversationType, replyContent, replySender, itemNbt));
        }
        return new ClientboundMessageSyncPayload(list);
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
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }


    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record StoredMessage(String senderName, UUID senderUuid, String content, long timestamp,
                                String conversationId, String conversationType,
                                String replyContent, String replySender,
                                String itemNbt) {}
}
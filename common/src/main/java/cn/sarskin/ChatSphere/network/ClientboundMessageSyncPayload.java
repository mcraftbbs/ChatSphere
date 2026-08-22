package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ClientboundMessageSyncPayload(List<StoredMessage> messages)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "message_sync");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundMessageSyncPayload p) {
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
            writeUuid(buf, m.messageId());
            buf.writeBoolean(m.isInput());
        }
    }

    public static ClientboundMessageSyncPayload read(FriendlyByteBuf buf) {
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
            UUID messageId = readUuid(buf);
            boolean isInput = buf.readBoolean();
            list.add(new StoredMessage(senderName, senderUuid, content, timestamp, conversationId, conversationType, replyContent, replySender, itemNbt, messageId, isInput));
        }
        return new ClientboundMessageSyncPayload(list);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }

    private static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        if (uuid == null) uuid = Util.NIL_UUID;
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(FriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }


    
    public record StoredMessage(String senderName, UUID senderUuid, String content, long timestamp,
                                String conversationId, String conversationType,
                                String replyContent, String replySender,
                                String itemNbt, UUID messageId, boolean isInput) {
        public StoredMessage(String senderName, UUID senderUuid, String content, long timestamp,
                             String conversationId, String conversationType,
                             String replyContent, String replySender,
                             String itemNbt, UUID messageId) {
            this(senderName, senderUuid, content, timestamp, conversationId, conversationType,
                    replyContent, replySender, itemNbt, messageId, false);
        }

        public StoredMessage(String senderName, UUID senderUuid, String content, long timestamp,
                             String conversationId, String conversationType,
                             String replyContent, String replySender,
                             String itemNbt) {
            this(senderName, senderUuid, content, timestamp, conversationId, conversationType,
                    replyContent, replySender, itemNbt, Util.NIL_UUID, false);
        }
    }
}
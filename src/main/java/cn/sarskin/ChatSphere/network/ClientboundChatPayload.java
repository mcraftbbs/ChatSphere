package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ClientboundChatPayload(StoredMessage message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chat"));

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
        return new ClientboundChatPayload(new StoredMessage(senderName, senderUuid, content, timestamp, conversationId, conversationType, replyContent, replySender, itemNbt));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            StoredMessage sm = message;
            UUID localPlayer = ctx.player() != null ? ctx.player().getUUID() : null;
            boolean isOwn = localPlayer != null && sm.senderUuid().equals(localPlayer);

            String convId = sm.conversationId() != null ? sm.conversationId() : ChatHistoryManager.DEFAULT_CHANNEL_ID;
            ChatMessageData.ConversationType ctype;
            if ("COMMAND".equals(sm.conversationType())) {
                ctype = ChatMessageData.ConversationType.COMMAND;
            } else if ("PRIVATE".equals(sm.conversationType())) {
                ctype = ChatMessageData.ConversationType.PRIVATE;
            } else {
                ctype = ChatMessageData.ConversationType.CHANNEL;
            }

            if (ctype == ChatMessageData.ConversationType.COMMAND) {
                String cmdText = sm.content() != null && !sm.content().isEmpty() ? sm.content() : sm.senderName();
                history.addCommandMessage(
                        Component.literal(cmdText),
                        sm.senderUuid(),
                        Component.literal(""),
                        isOwn);
            } else if (isOwn) {
                // Own messages already added locally by ModChatScreen.sendChatMessage() with reply data
            } else if (ctype == ChatMessageData.ConversationType.PRIVATE) {
                Component displayName = ChatHistoryManager.resolveOtherPartyName(convId, Component.literal(sm.senderName()));
                history.addPrivateConversation(convId, displayName);
                history.addMessage(
                        Component.literal(sm.senderName()),
                        sm.senderUuid(),
                        Component.literal(sm.content()),
                        convId,
                        ctype,
                        isOwn,
                        sm.replyContent(),
                        sm.replySender(),
                        sm.itemNbt());
            } else {
                history.addMessage(
                        Component.literal(sm.senderName()),
                        sm.senderUuid(),
                        Component.literal(sm.content()),
                        convId,
                        ctype,
                        isOwn,
                        sm.replyContent(),
                        sm.replySender(),
                        sm.itemNbt());
            }
        });
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

    public record StoredMessage(String senderName, UUID senderUuid, String content, long timestamp,
                                String conversationId, String conversationType,
                                String replyContent, String replySender,
                                String itemNbt) {}
}

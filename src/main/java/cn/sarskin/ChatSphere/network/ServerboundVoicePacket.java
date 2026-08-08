package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModVoiceStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ServerboundVoicePacket(
        UUID voiceMessageId,
        String conversationId,
        String conversationType,
        UUID senderUuid,
        int frameCount,
        byte[] audioData
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundVoicePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "voice_c2s"));

    public static final StreamCodec<ByteBuf, ServerboundVoicePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundVoicePacket::write, ServerboundVoicePacket::read);

    private static void write(ByteBuf buf, ServerboundVoicePacket p) {
        writeUuid(buf, p.voiceMessageId);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
        writeUuid(buf, p.senderUuid);
        buf.writeInt(p.frameCount);
        buf.writeInt(p.audioData.length);
        buf.writeBytes(p.audioData);
    }

    private static ServerboundVoicePacket read(ByteBuf buf) {
        UUID voiceMessageId = readUuid(buf);
        String convId = PayloadLimits.readUtf(buf);
        String convType = PayloadLimits.readUtf(buf);
        UUID senderUuid = readUuid(buf);
        int frameCount = buf.readInt();
        byte[] audioData = PayloadLimits.readBytes(buf, PayloadLimits.MAX_AUDIO_BYTES);
        if (frameCount < 0 || frameCount > PayloadLimits.MAX_AUDIO_BYTES) {
            throw new IllegalStateException("Frame count out of range: " + frameCount);
        }
        return new ServerboundVoicePacket(voiceMessageId, convId, convType, senderUuid, frameCount, audioData);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var server = player.getServer();
            if (server == null) return;

            UUID realUuid = player.getUUID();
            String senderStr = realUuid.toString();
            ClientboundVoicePacket relay = new ClientboundVoicePacket(
                    voiceMessageId, realUuid, conversationId, conversationType, frameCount, audioData);
            ModVoiceStorage storage = ModVoiceStorage.getInstance(server);
            ModServerChannels msc = ModServerChannels.getInstance(server);

            // Store the chat message placeholder in server message history for sync on reconnect
            String senderName = player.getName().getString();
            msc.addChatMessage(senderName, realUuid,
                    "VoiceMessage#" + voiceMessageId,
                    conversationId, conversationType, "", "", "");

            if ("CHANNEL".equals(conversationType)) {
                if (conversationId == null) return;
                List<String> recipients = msc.effectiveMembers(conversationId);
                if (!recipients.contains(senderStr)) return;
                if (msc.isMuted(conversationId, senderStr)) return;

                for (String memberUuid : recipients) {
                    if (memberUuid.equals(senderStr)) continue;
                    ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(memberUuid));
                    if (target != null) {
                        target.connection.send(new ClientboundCustomPayloadPacket(relay));
                    } else {
                        storage.store(voiceMessageId, senderStr, conversationId, conversationType, frameCount, audioData);
                    }
                }
            } else if ("PRIVATE".equals(conversationType) && conversationId != null && conversationId.contains(":")) {
                String[] parts = conversationId.split(":");
                if (parts.length != 2) return;
                String recipientStr = parts[0].equals(senderStr) ? parts[1] : parts[0];
                ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(recipientStr));
                if (target != null) {
                    target.connection.send(new ClientboundCustomPayloadPacket(relay));
                } else {
                    storage.store(voiceMessageId, senderStr, conversationId, conversationType, frameCount, audioData);
                }
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
}

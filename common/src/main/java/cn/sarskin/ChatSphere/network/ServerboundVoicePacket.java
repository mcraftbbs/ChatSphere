package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
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
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "voice_c2s"));

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
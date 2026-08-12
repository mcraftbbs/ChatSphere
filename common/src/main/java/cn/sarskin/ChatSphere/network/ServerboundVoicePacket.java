package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
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
)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "voice_c2s");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ServerboundVoicePacket p) {
        writeUuid(buf, p.voiceMessageId);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
        writeUuid(buf, p.senderUuid);
        buf.writeInt(p.frameCount);
        buf.writeInt(p.audioData.length);
        buf.writeBytes(p.audioData);
    }

    public static ServerboundVoicePacket read(FriendlyByteBuf buf) {
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
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(FriendlyByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
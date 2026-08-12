package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ClientboundVoicePacket(
        UUID voiceMessageId,
        UUID senderUuid,
        String conversationId,
        String conversationType,
        int frameCount,
        byte[] audioData
)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "voice_s2c");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundVoicePacket p) {
        writeUuid(buf, p.voiceMessageId);
        writeUuid(buf, p.senderUuid);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
        buf.writeInt(p.frameCount);
        buf.writeInt(p.audioData.length);
        buf.writeBytes(p.audioData);
    }

    public static ClientboundVoicePacket read(FriendlyByteBuf buf) {
        UUID voiceMessageId = readUuid(buf);
        UUID senderUuid = readUuid(buf);
        String convId = PayloadLimits.readUtf(buf);
        String convType = PayloadLimits.readUtf(buf);
        int frameCount = buf.readInt();
        byte[] audioData = PayloadLimits.readBytes(buf, PayloadLimits.MAX_AUDIO_BYTES);
        if (frameCount < 0 || frameCount > PayloadLimits.MAX_AUDIO_BYTES) {
            throw new IllegalStateException("Frame count out of range: " + frameCount);
        }
        return new ClientboundVoicePacket(voiceMessageId, senderUuid, convId, convType, frameCount, audioData);
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
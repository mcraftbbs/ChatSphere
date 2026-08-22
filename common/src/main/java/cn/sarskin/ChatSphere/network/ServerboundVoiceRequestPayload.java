package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client asks the server to re-send a voice message's audio by its voiceMessageId. */
public record ServerboundVoiceRequestPayload(UUID voiceMessageId) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "voice_request_c2s");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeLong(voiceMessageId.getMostSignificantBits());
        buf.writeLong(voiceMessageId.getLeastSignificantBits());
        return buf;
    }

    public static ServerboundVoiceRequestPayload read(FriendlyByteBuf buf) {
        UUID id = new UUID(buf.readLong(), buf.readLong());
        return new ServerboundVoiceRequestPayload(id);
    }
}

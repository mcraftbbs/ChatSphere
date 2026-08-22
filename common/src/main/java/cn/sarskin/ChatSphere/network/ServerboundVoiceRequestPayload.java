package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client asks the server to re-send a voice message's audio by its voiceMessageId. */
public record ServerboundVoiceRequestPayload(UUID voiceMessageId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundVoiceRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "voice_request_c2s"));

    public static final StreamCodec<ByteBuf, ServerboundVoiceRequestPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundVoiceRequestPayload::write, ServerboundVoiceRequestPayload::read);

    private static void write(ByteBuf buf, ServerboundVoiceRequestPayload p) {
        buf.writeLong(p.voiceMessageId.getMostSignificantBits());
        buf.writeLong(p.voiceMessageId.getLeastSignificantBits());
    }

    private static ServerboundVoiceRequestPayload read(ByteBuf buf) {
        UUID id = new UUID(buf.readLong(), buf.readLong());
        return new ServerboundVoiceRequestPayload(id);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

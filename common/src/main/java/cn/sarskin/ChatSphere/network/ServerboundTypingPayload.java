package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/** Client tells the server "I am typing here"; the server relays it to the other members. */
public record ServerboundTypingPayload(String conversationId, String conversationType) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundTypingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "typing"));

    public static final StreamCodec<ByteBuf, ServerboundTypingPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundTypingPayload::write, ServerboundTypingPayload::read);

    private static void write(ByteBuf buf, ServerboundTypingPayload p) {
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
    }

    private static ServerboundTypingPayload read(ByteBuf buf) {
        return new ServerboundTypingPayload(PayloadLimits.readUtf(buf), PayloadLimits.readUtf(buf));
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

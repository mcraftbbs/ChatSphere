package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/** Client tells the server "I am typing here"; the server relays it to the other members. */
public record ServerboundTypingPayload(String conversationId, String conversationType) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "typing");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ServerboundTypingPayload p) {
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
    }

    public static ServerboundTypingPayload read(FriendlyByteBuf buf) {
        return new ServerboundTypingPayload(readUtf(buf), readUtf(buf));
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return new String(PayloadLimits.readBytes(buf, PayloadLimits.MAX_UTF_BYTES), StandardCharsets.UTF_8);
    }
}

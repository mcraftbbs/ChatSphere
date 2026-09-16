package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Someone is typing in a conversation the local player can see. Never stored, purely live. */
public record ClientboundTypingPayload(UUID playerUuid, String playerName, String conversationId,
                                       String conversationType) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "typing_relay");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ClientboundTypingPayload p) {
        buf.writeUUID(p.playerUuid);
        writeUtf(buf, p.playerName);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
    }

    public static ClientboundTypingPayload read(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        return new ClientboundTypingPayload(uuid, readUtf(buf), readUtf(buf), readUtf(buf));
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

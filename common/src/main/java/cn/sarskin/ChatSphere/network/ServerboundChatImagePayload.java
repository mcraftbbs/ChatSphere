package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerImages;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/** One chunk of a chat image upload, or a request to send a stored image back. */
public record ServerboundChatImagePayload(Action action, String id, int partIndex, int partCount,
                                          int width, int height, String sourceUrl, byte[] data)
        implements CustomPacketPayload {

    public enum Action { UPLOAD, REQUEST }

    public static final CustomPacketPayload.Type<ServerboundChatImagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "chat_image"));

    public static final StreamCodec<ByteBuf, ServerboundChatImagePayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChatImagePayload::write, ServerboundChatImagePayload::read);

    private static void write(ByteBuf buf, ServerboundChatImagePayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.id);
        buf.writeInt(p.partIndex);
        buf.writeInt(p.partCount);
        buf.writeInt(p.width);
        buf.writeInt(p.height);
        writeUtf(buf, p.sourceUrl);
        buf.writeInt(p.data.length);
        buf.writeBytes(p.data);
    }

    private static ServerboundChatImagePayload read(ByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) throw new IllegalStateException("bad action");
        String id = PayloadLimits.readUtf(buf);
        int partIndex = buf.readInt();
        int partCount = buf.readInt();
        int width = buf.readInt();
        int height = buf.readInt();
        String sourceUrl = PayloadLimits.readUtf(buf);
        if (partIndex < 0 || partCount < 1 || partCount > ModServerImages.MAX_CHUNKS || partIndex >= partCount) {
            throw new IllegalStateException("bad chunk range");
        }
        byte[] data = PayloadLimits.readBytes(buf, ModServerImages.CHUNK_BYTES);
        return new ServerboundChatImagePayload(Action.values()[actionIdx], id, partIndex, partCount, width, height, sourceUrl, data);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static void sendToServer(ServerboundChatImagePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

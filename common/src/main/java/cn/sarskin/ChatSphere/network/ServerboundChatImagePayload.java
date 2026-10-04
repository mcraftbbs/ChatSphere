package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerImages;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * One chunk of a chat image upload, or a request to send a stored image back.
 *
 * Chunks stay under {@link ModServerImages#CHUNK_BYTES} because one 1.20.1 payload may not exceed 32767 bytes.
 */
public record ServerboundChatImagePayload(Action action, String id, int partIndex, int partCount,
                                          int width, int height, String sourceUrl, byte[] data) {

    public enum Action { UPLOAD, REQUEST }

    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "chat_image");
    private static final int MAX_STRING = 512;

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ServerboundChatImagePayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.id);
        buf.writeInt(p.partIndex);
        buf.writeInt(p.partCount);
        buf.writeInt(p.width);
        buf.writeInt(p.height);
        writeUtf(buf, p.sourceUrl);
        byte[] data = p.data != null ? p.data : new byte[0];
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static ServerboundChatImagePayload read(FriendlyByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) {
            throw new IllegalStateException("Unknown chat image action: " + actionIdx);
        }
        Action action = Action.values()[actionIdx];
        String id = readUtf(buf);
        int partIndex = buf.readInt();
        int partCount = buf.readInt();
        int width = buf.readInt();
        int height = buf.readInt();
        String sourceUrl = readUtf(buf);
        if (partIndex < 0 || partCount < 1 || partCount > ModServerImages.MAX_CHUNKS || partIndex >= partCount) {
            throw new IllegalStateException("Bad chat image chunk: " + partIndex + "/" + partCount);
        }
        int len = buf.readInt();
        if (len < 0 || len > ModServerImages.CHUNK_BYTES) {
            throw new IllegalStateException("Chat image chunk too large: " + len);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new ServerboundChatImagePayload(action, id, partIndex, partCount, width, height, sourceUrl, data);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_STRING) {
            throw new IllegalStateException("Chat image string too long: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void sendToServer(ServerboundChatImagePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundCustomPayloadPacket(ID, payload.toBuf()));
    }
}

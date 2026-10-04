package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerImages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;

/** Upload result, one chunk of a stored image, or a failure reason. */
public record ClientboundChatImagePayload(Action action, String id, int width, int height,
                                          int partIndex, int partCount, String reason, byte[] data) {

    public enum Action { ACCEPTED, DATA, FAILED }

    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "chat_image_relay");
    private static final int MAX_STRING = 512;

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ClientboundChatImagePayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.id);
        buf.writeInt(p.width);
        buf.writeInt(p.height);
        buf.writeInt(p.partIndex);
        buf.writeInt(p.partCount);
        writeUtf(buf, p.reason);
        byte[] data = p.data != null ? p.data : new byte[0];
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static ClientboundChatImagePayload read(FriendlyByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) {
            throw new IllegalStateException("Unknown chat image action: " + actionIdx);
        }
        Action action = Action.values()[actionIdx];
        String id = readUtf(buf);
        int width = buf.readInt();
        int height = buf.readInt();
        int partIndex = buf.readInt();
        int partCount = buf.readInt();
        String reason = readUtf(buf);
        if (partIndex < 0 || partCount < 0 || partCount > ModServerImages.MAX_CHUNKS) {
            throw new IllegalStateException("Bad chat image chunk: " + partIndex + "/" + partCount);
        }
        int len = buf.readInt();
        if (len < 0 || len > ModServerImages.CHUNK_BYTES) {
            throw new IllegalStateException("Chat image chunk too large: " + len);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new ClientboundChatImagePayload(action, id, width, height, partIndex, partCount, reason, data);
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

    public static ClientboundChatImagePayload accepted(String id, int width, int height) {
        return new ClientboundChatImagePayload(Action.ACCEPTED, id, width, height, 0, 0, "", new byte[0]);
    }

    public static ClientboundChatImagePayload data(String id, int partIndex, int partCount, byte[] data) {
        return new ClientboundChatImagePayload(Action.DATA, id, 0, 0, partIndex, partCount, "", data);
    }

    public static ClientboundChatImagePayload failed(String id, String reason) {
        return new ClientboundChatImagePayload(Action.FAILED, id, 0, 0, 0, 0, reason, new byte[0]);
    }

    public static void sendTo(ServerPlayer player, ClientboundChatImagePayload payload) {
        if (player == null) return;
        player.connection.send(new ClientboundCustomPayloadPacket(ID, payload.toBuf()));
    }
}

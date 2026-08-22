package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.client.emoji.EmojiFileGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * Server -> client custom emoji update: ADD carries the validated image bytes,
 * DELETE removes one. ADD data was already validated server-side, but the client
 * re-validates before writing to disk (defense in depth).
 */
public record ClientboundCustomEmojiPayload(Action action, String name, String channelId, byte[] data) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "emoji_update");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ClientboundCustomEmojiPayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.name);
        writeUtf(buf, p.channelId);
        byte[] data = p.data != null ? p.data : new byte[0];
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static ClientboundCustomEmojiPayload read(FriendlyByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) {
            throw new IllegalStateException("Unknown emoji action: " + actionIdx);
        }
        Action action = Action.values()[actionIdx];
        String name = readUtf(buf);
        String channelId = readChannelId(buf);
        int len = buf.readInt();
        if (len < 0 || len > EmojiFileGuard.MAX_BYTES) {
            throw new IllegalStateException("Emoji payload too large: " + len);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new ClientboundCustomEmojiPayload(action, name, channelId, data);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 64) {
            throw new IllegalStateException("Emoji name too long: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Channel ids run longer than names. */
    private static String readChannelId(FriendlyByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 256) {
            throw new IllegalStateException("Emoji channel id too long: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public enum Action { ADD, DELETE }
}

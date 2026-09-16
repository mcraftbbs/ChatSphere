package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client -> server custom emoji actions: ADD (upload bytes), DELETE,
 * SYNC_REQUEST (ask the server to push every server emoji to this client).
 *
 * An ADD image is larger than one 1.20.1 payload may carry (32767 bytes), so it travels as numbered
 * chunks of at most {@link #CHUNK_BYTES} and the server joins them back together.
 */
public record ServerboundCustomEmojiPayload(Action action, String name, String channelId, byte[] data,
                                            int index, int total) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "emoji_action");
    public static final int CHUNK_BYTES = 16 * 1024;
    // 512 KB cap / 16 KB per chunk = 32 chunks, plus headroom
    public static final int MAX_CHUNKS = 40;

    public ServerboundCustomEmojiPayload(Action action, String name, String channelId, byte[] data) {
        this(action, name, channelId, data, 0, 1);
    }

    /** One payload for small actions, a numbered series for an image that does not fit. */
    public static List<ServerboundCustomEmojiPayload> chunked(Action action, String name, String channelId, byte[] data) {
        byte[] bytes = data == null ? new byte[0] : data;
        if (action != Action.ADD || bytes.length <= CHUNK_BYTES) {
            return List.of(new ServerboundCustomEmojiPayload(action, name, channelId, bytes));
        }
        int count = (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        List<ServerboundCustomEmojiPayload> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * CHUNK_BYTES;
            int to = Math.min(bytes.length, from + CHUNK_BYTES);
            out.add(new ServerboundCustomEmojiPayload(action, name, channelId,
                    Arrays.copyOfRange(bytes, from, to), i, count));
        }
        return out;
    }

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    private static void write(FriendlyByteBuf buf, ServerboundCustomEmojiPayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.name);
        writeUtf(buf, p.channelId);
        buf.writeInt(p.index);
        buf.writeInt(p.total);
        byte[] data = p.data != null ? p.data : new byte[0];
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public static ServerboundCustomEmojiPayload read(FriendlyByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) {
            throw new IllegalStateException("Unknown emoji action: " + actionIdx);
        }
        Action action = Action.values()[actionIdx];
        String name = readUtf(buf);
        String channelId = readChannelId(buf);
        int index = buf.readInt();
        int total = buf.readInt();
        if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) {
            throw new IllegalStateException("Bad emoji chunk: " + index + "/" + total);
        }
        int len = buf.readInt();
        if (len < 0 || len > CHUNK_BYTES) {
            throw new IllegalStateException("Emoji chunk too large: " + len);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new ServerboundCustomEmojiPayload(action, name, channelId, data, index, total);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
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

    public enum Action { ADD, DELETE, SYNC_REQUEST }
}

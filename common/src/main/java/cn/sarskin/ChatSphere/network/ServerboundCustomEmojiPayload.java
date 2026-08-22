package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.client.emoji.EmojiFileGuard;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * Client -> server custom emoji actions: ADD (upload bytes), DELETE,
 * SYNC_REQUEST (ask the server to push every server emoji to this client).
 * The reader hard-rejects oversized payloads so a malicious client cannot
 * push more than {@link EmojiFileGuard#MAX_BYTES} per emoji.
 */
public record ServerboundCustomEmojiPayload(Action action, String name, String channelId, byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundCustomEmojiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "emoji_action"));

    public static final StreamCodec<ByteBuf, ServerboundCustomEmojiPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundCustomEmojiPayload::write, ServerboundCustomEmojiPayload::read);

    private static void write(ByteBuf buf, ServerboundCustomEmojiPayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.name);
        writeUtf(buf, p.channelId);
        byte[] data = p.data != null ? p.data : new byte[0];
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static ServerboundCustomEmojiPayload read(ByteBuf buf) {
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
        return new ServerboundCustomEmojiPayload(action, name, channelId, data);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 64) {
            throw new IllegalStateException("Emoji name too long: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeChannelId(ByteBuf buf, String s) {
        writeUtf(buf, s == null ? "" : s);
    }

    /** Channel ids run longer than names. */
    private static String readChannelId(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > 256) {
            throw new IllegalStateException("Emoji channel id too long: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action { ADD, DELETE, SYNC_REQUEST }
}

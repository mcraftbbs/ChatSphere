package cn.sarskin.ChatSphere.network;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared decode guards for untrusted network input. All payload decoders
 * must go through these so a malicious peer cannot allocate unbounded
 * buffers (OOM/DoS) or feed oversized strings/lists.
 */
public final class PayloadLimits {
    public static final int MAX_UTF_BYTES = 32 * 1024;
    public static final int MAX_STRINGS_PER_LIST = 256;
    public static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MESSAGES = 2048;
    public static final int MAX_CHANNELS = 512;
    public static final int MAX_PLAYERS = 1024;
    public static final int MAX_VOICE_ROOMS = 64;

    private PayloadLimits() {}

    public static String readUtf(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_UTF_BYTES) {
            throw new IllegalStateException("String length out of range: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static List<String> readStringList(ByteBuf buf, int maxEntries) {
        int n = buf.readInt();
        if (n < 0 || n > maxEntries) {
            throw new IllegalStateException("List length out of range: " + n);
        }
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(readUtf(buf));
        }
        return list;
    }

    public static int readCount(ByteBuf buf, int max) {
        int n = buf.readInt();
        if (n < 0 || n > max) {
            throw new IllegalStateException("Count out of range: " + n);
        }
        return n;
    }

    public static byte[] readBytes(ByteBuf buf, int max) {
        int len = buf.readInt();
        if (len < 0 || len > max) {
            throw new IllegalStateException("Byte array length out of range: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return bytes;
    }
}

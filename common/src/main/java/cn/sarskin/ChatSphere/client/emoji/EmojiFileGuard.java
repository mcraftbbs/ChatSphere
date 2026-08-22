package cn.sarskin.ChatSphere.client.emoji;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Byte-only validation of uploaded emoji (no image decoder on untrusted data), identical on server and client; errors are lang keys.
 */
public final class EmojiFileGuard {
    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_DIM = 512;
    /** Animated GIFs are stricter: smaller canvas, capped frames, capped sprite-sheet width. */
    public static final int MAX_ANIM_DIM = 256;
    public static final int MAX_FRAMES = 30;
    public static final int MAX_SHEET_W = 4096;
    public static final int MAX_NAME_LEN = 32;
    public static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_+-]{1," + MAX_NAME_LEN + "}");

    // err_big mentions MAX_DIM, err_anim mentions the animation caps — keep in sync
    public static final String ERR_NAME = "chatsphere.emoji.err_name";
    public static final String ERR_EMPTY = "chatsphere.emoji.err_empty";
    public static final String ERR_SIZE = "chatsphere.emoji.err_size";
    public static final String ERR_FORMAT = "chatsphere.emoji.err_format";
    public static final String ERR_HEADER = "chatsphere.emoji.err_header";
    public static final String ERR_DIMS = "chatsphere.emoji.err_dims";
    public static final String ERR_BIG = "chatsphere.emoji.err_big";
    public static final String ERR_ANIM = "chatsphere.emoji.err_anim";

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};

    private EmojiFileGuard() {
    }

    /** Returns null when the (name, data) pair is acceptable, otherwise a lang key. */
    public static String validate(String name, byte[] data) {
        if (name == null || !NAME.matcher(name).matches()) {
            return ERR_NAME;
        }
        if (data == null || data.length == 0) {
            return ERR_EMPTY;
        }
        if (data.length > MAX_BYTES) {
            return ERR_SIZE;
        }
        int[] dims;
        if (isPng(data)) {
            dims = pngDimensions(data);
        } else if (isGif(data)) {
            dims = gifDimensions(data);
            if (dims != null) {
                int frames = gifFrameCount(data);
                if (frames < 1) {
                    return ERR_HEADER;
                }
                if (frames > 1
                        && (dims[0] > MAX_ANIM_DIM || dims[1] > MAX_ANIM_DIM
                        || frames > MAX_FRAMES || dims[0] * frames > MAX_SHEET_W)) {
                    return ERR_ANIM;
                }
            }
        } else {
            return ERR_FORMAT;
        }
        if (dims == null) {
            return ERR_HEADER;
        }
        if (dims[0] <= 0 || dims[1] <= 0) {
            return ERR_DIMS;
        }
        if (dims[0] > MAX_DIM || dims[1] > MAX_DIM) {
            return ERR_BIG;
        }
        return null;
    }

    /** File extension implied by the magic number; null when not an accepted image. */
    public static String extensionFor(byte[] data) {
        if (isPng(data)) return "png";
        if (isGif(data)) return "gif";
        return null;
    }

    private static boolean isPng(byte[] d) {
        if (d.length < PNG_MAGIC.length) return false;
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (d[i] != PNG_MAGIC[i]) return false;
        }
        return true;
    }

    /** PNG width/height from the IHDR chunk (big-endian). Returns null when truncated. */
    private static int[] pngDimensions(byte[] d) {
        // signature(8) + length(4) + "IHDR"(4) + width(4) + height(4)
        if (d.length < 8 + 4 + 4 + 4 + 4) return null;
        if (d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') return null;
        int w = be32(d, 16);
        int h = be32(d, 20);
        return new int[]{w, h};
    }

    private static boolean isGif(byte[] d) {
        if (d.length < 6) return false;
        for (int i = 0; i < GIF_MAGIC.length; i++) {
            if (d[i] != GIF_MAGIC[i]) return false;
        }
        return d[5] == 'a' && (d[4] == '7' || d[4] == '9');
    }

    /** GIF width/height from the logical screen descriptor (little-endian). Returns null when truncated. */
    private static int[] gifDimensions(byte[] d) {
        // signature(6) + width(2 LE) + height(2 LE)
        if (d.length < 10) return null;
        int w = (d[6] & 0xFF) | ((d[7] & 0xFF) << 8);
        int h = (d[8] & 0xFF) | ((d[9] & 0xFF) << 8);
        return new int[]{w, h};
    }

    /** GIF frame count by walking block structure (no decode); -1 when malformed. Stops early once a cap is exceeded. */
    public static int gifFrameCount(byte[] d) {
        if (d.length < 13) return -1;
        // signature(6) + logical screen descriptor(7), then optional global color table
        int i = 13;
        int packed = d[10] & 0xFF;
        if ((packed & 0x80) != 0) {
            i += 3 * (1 << ((packed & 7) + 1));
            if (i > d.length) return -1;
        }
        int w = (d[6] & 0xFF) | ((d[7] & 0xFF) << 8);
        int h = (d[8] & 0xFF) | ((d[9] & 0xFF) << 8);
        int frames = 0;
        while (i < d.length) {
            int b = d[i] & 0xFF;
            if (b == 0x3B) {
                return frames;
            }
            if (b == 0x21) {
                // extension introducer + label, then data sub-blocks
                if (i + 2 > d.length) return -1;
                i = skipSubBlocks(d, i + 2);
                if (i < 0) return -1;
            } else if (b == 0x2C) {
                frames++;
                // stop once a cap is exceeded; the caller maps the count to ERR_ANIM
                if (frames > MAX_FRAMES || frames * w > MAX_SHEET_W
                        || (frames > 1 && (w > MAX_ANIM_DIM || h > MAX_ANIM_DIM))) {
                    return frames;
                }
                // image descriptor(9) + optional local color table + LZW min code size + data sub-blocks
                if (i + 9 >= d.length) return -1;
                int p = d[i + 9] & 0xFF;
                i += 10;
                if ((p & 0x80) != 0) {
                    i += 3 * (1 << ((p & 7) + 1));
                    if (i > d.length) return -1;
                }
                if (i >= d.length) return -1;
                i += 1;
                i = skipSubBlocks(d, i);
                if (i < 0) return -1;
            } else {
                return -1;
            }
        }
        return frames;
    }

    /** Skip a sub-block run (length byte + payload until 0x00); returns the index after the terminator, or -1. */
    private static int skipSubBlocks(byte[] d, int i) {
        while (i < d.length && d[i] != 0) {
            int len = d[i] & 0xFF;
            i += 1 + len;
        }
        if (i >= d.length) return -1;
        return i + 1;
    }

    private static int be32(byte[] d, int off) {
        return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }

    /** Channel id -> hex (no path separators survive, reversible); used for per-channel storage dirs. */
    public static String channelDirName(String channelId) {
        if (channelId == null || channelId.isEmpty()) return "";
        byte[] bytes = channelId.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Inverse of {@link #channelDirName}; "" on malformed input. */
    public static String channelDirDecode(String hex) {
        if (hex == null || hex.length() % 2 != 0) return "";
        try {
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                int hi = Character.digit(hex.charAt(i * 2), 16);
                int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
                if (hi < 0 || lo < 0) return "";
                out[i] = (byte) ((hi << 4) | lo);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}

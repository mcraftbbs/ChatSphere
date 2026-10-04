package cn.sarskin.ChatSphere.client.image;

import cn.sarskin.ChatSphere.config.ModServerConfig;

/** Byte level checks for chat images; the caps come from the server config shipped to clients. */
public final class ChatImageGuard {
    public static final String ERR_DISABLED = "chatsphere.image.err_disabled";
    public static final String ERR_SIZE = "chatsphere.image.err_size";
    public static final String ERR_FORMAT = "chatsphere.image.err_format";
    public static final String ERR_DIMS = "chatsphere.image.err_dims";
    public static final String ERR_GIF = "chatsphere.image.err_gif";

    private ChatImageGuard() {}

    /** Returns null when the bytes are acceptable, else a lang key; {@code dimsOut} gets width/height. */
    public static String validate(byte[] data, int[] dimsOut) {
        if (!ModServerConfig.CONFIG.chatImageEnabled.get()) return ERR_DISABLED;
        if (data == null || data.length == 0) return ERR_FORMAT;
        if (isGif(data)) return ERR_GIF;
        long maxBytes = (long) Math.max(1, ModServerConfig.CONFIG.chatImageMaxKb.get()) * 1024L;
        if (data.length > maxBytes) return ERR_SIZE;
        int[] dims = isPng(data) ? pngDims(data) : (isJpeg(data) ? jpegDims(data) : null);
        if (dims == null || dims[0] <= 0 || dims[1] <= 0) return ERR_FORMAT;
        int maxDim = Math.max(16, ModServerConfig.CONFIG.chatImageMaxDim.get());
        long maxPixels = Math.max(16L * 16L, (long) ModServerConfig.CONFIG.chatImageMaxPixels.get());
        if (dims[0] > maxDim || dims[1] > maxDim || (long) dims[0] * dims[1] > maxPixels) return ERR_DIMS;
        if (dimsOut != null && dimsOut.length >= 2) {
            dimsOut[0] = dims[0];
            dimsOut[1] = dims[1];
        }
        return null;
    }

    /** Bytes the texture loader can read: png passes through, jpeg and webp are converted. */
    public static byte[] pngBytes(byte[] data) {
        if (data == null || data.length == 0) return null;
        if (strictPng(data)) return data;
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (image == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            return javax.imageio.ImageIO.write(image, "png", out) ? out.toByteArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Minecraft reads png only, so anything else has to be converted first. */
    static boolean strictPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && (d[4] & 0xFF) == 0x0D && (d[5] & 0xFF) == 0x0A && (d[6] & 0xFF) == 0x1A && (d[7] & 0xFF) == 0x0A;
    }

    public static boolean isPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }

    public static boolean isJpeg(byte[] d) {
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    public static boolean isGif(byte[] d) {
        return d.length >= 4 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8';
    }

    /** Extension for a validated image, used as the stored file name suffix. */
    public static String extensionFor(byte[] data) {
        if (isPng(data)) return "png";
        if (isJpeg(data)) return "jpg";
        return null;
    }

    private static int[] pngDims(byte[] d) {
        if (d.length < 24) return null;
        int w = ((d[16] & 0xFF) << 24) | ((d[17] & 0xFF) << 16) | ((d[18] & 0xFF) << 8) | (d[19] & 0xFF);
        int h = ((d[20] & 0xFF) << 24) | ((d[21] & 0xFF) << 16) | ((d[22] & 0xFF) << 8) | (d[23] & 0xFF);
        return new int[]{w, h};
    }

    /** Walks the JPEG marker chain to the first start-of-frame segment. */
    private static int[] jpegDims(byte[] d) {
        int i = 2;
        while (i + 9 < d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int length = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
            if (length < 2) return null;
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                int h = ((d[i + 5] & 0xFF) << 8) | (d[i + 6] & 0xFF);
                int w = ((d[i + 7] & 0xFF) << 8) | (d[i + 8] & 0xFF);
                return new int[]{w, h};
            }
            i += 2 + length;
        }
        return null;
    }
}

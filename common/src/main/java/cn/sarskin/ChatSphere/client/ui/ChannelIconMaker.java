package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.client.image.ChatImageGuard;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Prepares a dropped image for use as a channel icon.
 *
 * <p>The icon keeps the source aspect ratio and never upscales; only pictures above the cap are
 * shrunk, so a small icon stays pixel sharp instead of being blown up and blurred. Cropping to a
 * square happens on screen through a rounded clip, not here.</p>
 */
public final class ChannelIconMaker {

    /** Upper bound on either side; anything smaller is uploaded untouched. */
    private static final int MAX_SIDE = 256;

    private ChannelIconMaker() {}

    /** Returns png bytes at most {@code cap} px on the long side, or null when unreadable. */
    public static byte[] toIconPng(byte[] raw, int cap) {
        if (raw == null || raw.length == 0) return null;
        int limit = cap > 0 ? cap : MAX_SIDE;
        try {
            byte[] png = ChatImageGuard.pngBytes(raw);
            if (png == null) return null;
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) return null;

            int longSide = Math.max(src.getWidth(), src.getHeight());
            BufferedImage out;
            if (longSide <= limit) {
                out = src;
            } else {
                float scale = (float) limit / longSide;
                int w = Math.max(1, Math.round(src.getWidth() * scale));
                int h = Math.max(1, Math.round(src.getHeight() * scale));
                out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = out.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(src, 0, 0, w, h, null);
                } finally {
                    g.dispose();
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}

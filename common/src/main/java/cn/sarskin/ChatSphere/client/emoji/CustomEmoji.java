package cn.sarskin.ChatSphere.client.emoji;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * User-uploaded emoji; animated GIFs live in one sprite-sheet texture, frameCount > 1 picks the frame by wall-clock time.
 * channelId is null for local emoji, "" for public server emoji, the channel id for channel-scoped ones.
 */
public record CustomEmoji(String shortcode, ResourceLocation texture, int width, int height, long fileSize,
                          boolean serverSynced, String channelId, int frameCount, int[] frameDelaysMs, int frameW, int frameH) {
    public String token() {
        return ":" + shortcode + ":";
    }

    public boolean animated() {
        return frameCount > 1;
    }

    /** Frame index at the given wall-clock time; 0 for static emoji. */
    public int frameAt(long timeMs) {
        if (frameCount <= 1) return 0;
        int total = 0;
        for (int d : frameDelaysMs) total += d;
        if (total <= 0) return 0;
        long t = timeMs % total;
        for (int i = 0; i < frameCount; i++) {
            t -= frameDelaysMs[i % frameDelaysMs.length];
            if (t < 0) return i;
        }
        return frameCount - 1;
    }

    /** Draw the current animation frame at (x, y) sized (w, h); static emoji draw their single frame. */
    public void blit(GuiGraphics g, int x, int y, int w, int h) {
        int frame = frameAt(System.currentTimeMillis());
        int u = frame * frameW;
        // mixed blit: floats are the u0/v0 start, ints are the region width/height
        g.blit(texture, x, y, w, h, u, 0f, frameW, frameH, frameCount * frameW, frameH);
    }
}

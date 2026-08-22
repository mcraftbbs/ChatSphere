package cn.sarskin.ChatSphere.client.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

public final class Ui {
    private Ui() {}

    private static final Map<Integer, int[]> CORNER_ALPHA_CACHE = new HashMap<>();

    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        switch (Theme.cornerStyle()) {
            case 0:
                g.fill(x, y, x + w, y + h, color);
                return;
            case 1:
            case 3:
                fillPixelRounded(g, x, y, w, h, r, color);
                return;
            default:
                fillOriginalRounded(g, x, y, w, h, r, color);
        }
    }

    public static void fillBubbleRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        fillRoundedRect(g, x, y, w, h, r, color);
    }

    /** Vertical gradient bubble fill (custom theme feature); rounded corners preserved. */
    public static void fillBubbleGradient(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        g.fillGradient(x + r, y + r, x + w - r, y + h - r,
                interpColor(top, bottom, (r + 0.5f) / h), interpColor(top, bottom, (h - r - 0.5f) / h));
        g.fillGradient(x + r, y, x + w - r, y + r, top, interpColor(top, bottom, (r - 0.5f) / h));
        g.fillGradient(x + r, y + h - r, x + w - r, y + h,
                interpColor(top, bottom, (h - r + 0.5f) / h), bottom);
        int sideTop = interpColor(top, bottom, (r + 0.5f) / h);
        int sideBot = interpColor(top, bottom, (h - r - 0.5f) / h);
        g.fillGradient(x, y + r, x + r, y + h - r, sideTop, sideBot);
        g.fillGradient(x + w - r, y + r, x + w, y + h - r, sideTop, sideBot);
        for (int dy = 0; dy < r; dy++) {
            int c = interpColor(top, bottom, (dy + 0.5f) / h);
            int dx = (int) Math.sqrt(r * r - dy * dy);
            int sy1 = y + r - dy - 1;
            g.fill(x + r - dx, sy1, x + r, sy1 + 1, c);
            g.fill(x + w - r, sy1, x + w - r + dx, sy1 + 1, c);
            int sy2 = y + h - r + dy;
            g.fill(x + r - dx, sy2, x + r, sy2 + 1, c);
            g.fill(x + w - r, sy2, x + w - r + dx, sy2 + 1, c);
        }
    }

    private static int interpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    /** Rounded border tracing the fill's arc; atlas ring tiles with a per-pixel fallback. */
    public static void renderRoundedOutline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        // Square corner style: sharp outline to match the sharp fill
        if (Theme.cornerStyle() == 0) {
            g.renderOutline(x, y, w, h, color);
            return;
        }
        if (UiRoundedAtlas.fillRoundedBorder(g, x, y, w, h, r, 1, color)) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 0) {
            g.renderOutline(x, y, w, h, color);
            return;
        }
        int bw = 1;
        int x1 = x + w, y1 = y + h;
        g.fill(x + r, y, x1 - r, y + bw, color);
        g.fill(x + r, y1 - bw, x1 - r, y1, color);
        g.fill(x, y + r, x + bw, y1 - r, color);
        g.fill(x1 - bw, y + r, x1, y1 - r, color);
        if (r <= bw) return;
        int colorA = color >>> 24;
        for (int dy = 0; dy < r; dy++) {
            double v = r - dy - 0.5;
            for (int col = 0; col < r; col++) {
                double dx = r - col - 0.5;
                double d = Math.sqrt(dx * dx + v * v);
                double outer = Math.max(0.0, Math.min(1.0, r + 0.5 - d));
                double inner = Math.max(0.0, Math.min(1.0, r - bw + 0.5 - d));
                int ring = (int) Math.round(255 * Math.max(0.0, outer - inner));
                if (ring == 0) continue;
                int a = (colorA * ring) / 255;
                if (a == 0) continue;
                int ac = (a << 24) | (color & 0x00FFFFFF);
                g.fill(x + col, y + dy, x + col + 1, y + dy + 1, ac);
                g.fill(x1 - 1 - col, y + dy, x1 - col, y + dy + 1, ac);
                g.fill(x + col, y1 - 1 - dy, x + col + 1, y1 - dy, ac);
                g.fill(x1 - 1 - col, y1 - 1 - dy, x1 - col, y1 - dy, ac);
            }
        }
    }

    /** Cut rounded corners out of a blitted avatar texture (corner pixels filled with the given color). */
    public static void fillAvatarCorners(GuiGraphics g, int x, int y, int size, int r, int color) {
        r = Math.min(r, size / 2);
        if (r <= 0) return;
        int x1 = x + size, y1 = y + size;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                double d = Math.sqrt((dx + 0.5 - r) * (dx + 0.5 - r) + (dy + 0.5 - r) * (dy + 0.5 - r));
                if (d <= r) continue;
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color);
                g.fill(x1 - 1 - dx, y + dy, x1 - dx, y + dy + 1, color);
                g.fill(x + dx, y1 - 1 - dy, x + dx + 1, y1 - dy, color);
                g.fill(x1 - 1 - dx, y1 - 1 - dy, x1 - dx, y1 - dy, color);
            }
        }
    }

    public static void fillRoundedRectStyle(GuiGraphics g, int style, int x, int y, int w, int h, int r, int color) {
        switch (style) {
            case 0:
                g.fill(x, y, x + w, y + h, color);
                return;
            case 1:
            case 3:
                fillPixelRounded(g, x, y, w, h, r, color);
                return;
            default:
                fillOriginalRounded(g, x, y, w, h, r, color);
        }
    }

    private static void fillPixelRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (UiRoundedAtlas.fillRounded(g, x, y, w, h, r, color)) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int x1 = x + w;
        int y1 = y + h;
        g.fill(x + r, y + r, x1 - r, y1 - r, color);
        g.fill(x + r, y, x1 - r, y + r, color);
        g.fill(x + r, y1 - r, x1 - r, y1, color);
        g.fill(x, y + r, x + r, y1 - r, color);
        g.fill(x1 - r, y + r, x1, y1 - r, color);
        int[] alphas = cornerAlphas(r);
        int colorA = color >>> 24;
        for (int dy = 0; dy < r; dy++) {
            int topY = y + dy;
            int botY = y1 - 1 - dy;
            for (int col = 0; col < r; col++) {
                int a = (colorA * alphas[dy * r + col]) / 255;
                if (a == 0) continue;
                int ac = (a << 24) | (color & 0x00FFFFFF);
                g.fill(x + col, topY, x + col + 1, topY + 1, ac);
                g.fill(x1 - 1 - col, topY, x1 - col, topY + 1, ac);
                g.fill(x + col, botY, x + col + 1, botY + 1, ac);
                g.fill(x1 - 1 - col, botY, x1 - col, botY + 1, ac);
            }
        }
    }

    private static int[] cornerAlphas(int r) {
        int[] cached = CORNER_ALPHA_CACHE.get(r);
        if (cached != null) return cached;
        int[] table = new int[r * r];
        for (int dy = 0; dy < r; dy++) {
            double v = r - dy - 0.5;
            for (int col = 0; col < r; col++) {
                double dx = r - col - 0.5;
                double d = Math.sqrt(dx * dx + v * v);
                double a = Math.max(0.0, Math.min(1.0, r + 0.5 - d));
                table[dy * r + col] = (int) (255 * a);
            }
        }
        CORNER_ALPHA_CACHE.put(r, table);
        return table;
    }

    private static void fillOriginalRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y + r, x + w - r, y + h - r, color);
        g.fill(x + r, y, x + w - r, y + r, color);
        g.fill(x + r, y + h - r, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int dy = 0; dy < r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            int sy1 = y + r - dy - 1;
            g.fill(x + r - dx, sy1, x + r, sy1 + 1, color);
            g.fill(x + w - r, sy1, x + w - r + dx, sy1 + 1, color);
            int sy2 = y + h - r + dy;
            g.fill(x + r - dx, sy2, x + r, sy2 + 1, color);
            g.fill(x + w - r, sy2, x + w - r + dx, sy2 + 1, color);
        }
    }

    /** Multiply the alpha channel by [0..1] opacity; unchanged when fully opaque/transparent. */
    public static int withOpacity(int color, float opacity) {
        if (opacity <= 0f) return color & 0x00FFFFFF;
        if (opacity >= 1f) return color;
        int alpha = Math.round(((color >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** True when the color has any visible alpha (skip fully transparent draws). */
    public static boolean visible(int color) {
        return (color >>> 24) != 0;
    }

    /** True when (px, py) is inside the rounded rect, matching the visual corner shape. */
    public static boolean containsRounded(int x, int y, int w, int h, int r, int px, int py) {
        if (px < x || px >= x + w || py < y || py >= y + h) return false;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) return true;
        if (px >= x + r && px < x + w - r) return true;
        if (py >= y + r && py < y + h - r) return true;
        int row = py - y;
        int edgeRow = Math.min(row, h - 1 - row);
        int edgeInset = edgeRow >= r ? 0 : cornerInset(r, edgeRow);
        return px >= x + edgeInset && px < x + w - edgeInset;
    }

    /** Runs draw clipped to the rounded rect (middle band + per-row corner scissor bands). */
    public static void withRoundedClip(GuiGraphics g, int x, int y, int w, int h, int r, Runnable draw) {
        if (g == null || draw == null || w <= 0 || h <= 0) return;
        // Square corner style: plain rectangular clip
        if (Theme.cornerStyle() == 0) {
            g.enableScissor(x, y, x + w, y + h);
            try {
                draw.run();
            } finally {
                g.disableScissor();
            }
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        int middleTop = y + r;
        int middleBottom = y + h - r;
        if (middleBottom > middleTop) {
            drawClippedBand(g, x, middleTop, x + w, middleBottom, draw);
        }
        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            drawClippedBand(g, x + inset, y + row, x + w - inset, y + row + 1, draw);
            drawClippedBand(g, x + inset, y + h - row - 1, x + w - inset, y + h - row, draw);
        }
    }

    private static void drawClippedBand(GuiGraphics g, int left, int top, int right, int bottom, Runnable draw) {
        if (right <= left || bottom <= top) return;
        g.enableScissor(left, top, right, bottom);
        try {
            draw.run();
        } finally {
            g.disableScissor();
        }
    }

    /** Horizontal inset (px) of the arc at a given corner row; same geometry as cornerAlphas. */
    private static int cornerInset(int radius, int row) {
        double centerDistance = radius - row - 0.5D;
        double horizontal = Math.sqrt(Math.max(0.0D, radius * radius - centerDistance * centerDistance));
        return Math.max(0, radius - (int) Math.floor(horizontal));
    }
}

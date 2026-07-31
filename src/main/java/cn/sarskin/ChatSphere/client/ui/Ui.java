package cn.sarskin.ChatSphere.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class Ui {
    private Ui() {}

    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        switch (Theme.cornerStyle()) {
            case 1:
                fillPixelRounded(g, x, y, w, h, r, color);
                return;
            default:
                g.fill(x, y, x + w, y + h, color);
        }
    }

    public static void fillBubbleRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (Theme.cornerStyle() == 2) {
            fillOriginalRounded(g, x, y, w, h, r, color);
            return;
        }
        fillRoundedRect(g, x, y, w, h, r, color);
    }

    public static void fillRoundedRectStyle(GuiGraphics g, int style, int x, int y, int w, int h, int r, int color) {
        switch (style) {
            case 0:
                g.fill(x, y, x + w, y + h, color);
                return;
            case 1:
                fillPixelRounded(g, x, y, w, h, r, color);
                return;
            default:
                fillOriginalRounded(g, x, y, w, h, r, color);
        }
    }

    private static void fillPixelRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        fillCoverageRounded(g, x, y, w, h, r, color, 1.0);
    }

    private static void fillCoverageRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color, double band) {
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
        for (int dy = 0; dy < r; dy++) {
            double v = r - dy - 0.5;
            int topY = y + dy;
            int botY = y1 - 1 - dy;
            for (int col = 0; col < r; col++) {
                double dx = r - col - 0.5;
                double d = Math.sqrt(dx * dx + v * v);
                int a = coverAlpha(color, r, d, band);
                g.fill(x + col, topY, x + col + 1, topY + 1, a);
                g.fill(x1 - 1 - col, topY, x1 - col, topY + 1, a);
                g.fill(x + col, botY, x + col + 1, botY + 1, a);
                g.fill(x1 - 1 - col, botY, x1 - col, botY + 1, a);
            }
        }
    }

    private static int coverAlpha(int color, int r, double dist, double band) {
        double t = (r + band - 0.5 - dist) / band;
        double a = Math.max(0.0, Math.min(1.0, t));
        if (a <= 0.001) return 0;
        int alpha = (int) ((color >>> 24) * a);
        return (alpha << 24) | (color & 0x00FFFFFF);
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
}

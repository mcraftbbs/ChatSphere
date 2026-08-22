package cn.sarskin.ChatSphere.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * White-mask atlas for rounded fills/borders, baked at 2x GUI density with
 * linear filtering; corners are drawn as tinted quads.
 */
public final class UiRoundedAtlas {
    private static final int MAX_RADIUS = 16;
    private static final int MAX_BORDER_WIDTH = 4;
    private static final int TILE_LOGICAL_SIZE = 32;
    private static final int COLUMN_COUNT = 10;
    private static final int MAX_PIXEL_SCALE = 8;

    private static ResourceLocation textureId;
    private static DynamicTexture texture;
    private static int pixelScale = -1;
    private static int tilePx;
    private static int atlasWidth;
    private static int atlasHeight;

    private UiRoundedAtlas() {}

    /** Rounded fill via atlas; returns false to let the caller fall back. */
    public static boolean fillRounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return true;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0 || r > MAX_RADIUS) return false;
        if (!ensure()) return false;

        int entry = r - 1;
        int u = entry % COLUMN_COUNT * tilePx;
        int v = entry / COLUMN_COUNT * tilePx;
        int src = r * pixelScale;
        int x1 = x + w, y1 = y + h;

        g.fill(x + r, y, x1 - r, y1, color);
        g.fill(x, y + r, x + r, y1 - r, color);
        g.fill(x1 - r, y + r, x1, y1 - r, color);

        blitCorner(g, x, y, u, v, src, color);
        blitCorner(g, x1 - r, y, u + src, v, src, color);
        blitCorner(g, x, y1 - r, u, v + src, src, color);
        blitCorner(g, x1 - r, y1 - r, u + src, v + src, src, color);
        return true;
    }

    /** Rounded border tracing the arc; returns false to let the caller fall back. */
    public static boolean fillRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int bw, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return true;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 0 || r > MAX_RADIUS || bw <= 0) return false;
        bw = Math.min(bw, MAX_BORDER_WIDTH);
        if (!ensure()) return false;

        int entry = MAX_RADIUS + (bw - 1) * MAX_RADIUS + (r - 1);
        int u = entry % COLUMN_COUNT * tilePx;
        int v = entry / COLUMN_COUNT * tilePx;
        int src = r * pixelScale;
        int x1 = x + w, y1 = y + h;

        g.fill(x + r, y, x1 - r, y + bw, color);
        g.fill(x + r, y1 - bw, x1 - r, y1, color);
        g.fill(x, y + r, x + bw, y1 - r, color);
        g.fill(x1 - bw, y + r, x1, y1 - r, color);

        blitCorner(g, x, y, u, v, src, color);
        blitCorner(g, x1 - r, y, u + src, v, src, color);
        blitCorner(g, x, y1 - r, u, v + src, src, color);
        blitCorner(g, x1 - r, y1 - r, u + src, v + src, src, color);
        return true;
    }

    /** One corner quad with the tint baked into the vertices; samples a r*pixelScale
     *  region of the atlas at 1/pixelScale pose scale (smoothed by linear filtering). */
    private static void blitCorner(GuiGraphics g, int px, int py, int u, int v, int src, int color) {
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(1f / pixelScale, 1f / pixelScale, 1f);
        Matrix4f pose = g.pose().last().pose();
        float u0 = u / (float) atlasWidth;
        float v0 = v / (float) atlasHeight;
        float u1 = (u + src) / (float) atlasWidth;
        float v1 = (v + src) / (float) atlasHeight;
        float cr = ((color >> 16) & 0xFF) / 255f;
        float cg = ((color >> 8) & 0xFF) / 255f;
        float cb = (color & 0xFF) / 255f;
        float ca = (color >>> 24) / 255f;
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(pose, 0, 0, 0).uv(u0, v0).color(cr, cg, cb, ca).endVertex();
        bb.vertex(pose, 0, src, 0).uv(u0, v1).color(cr, cg, cb, ca).endVertex();
        bb.vertex(pose, src, src, 0).uv(u1, v1).color(cr, cg, cb, ca).endVertex();
        bb.vertex(pose, src, 0, 0).uv(u1, v0).color(cr, cg, cb, ca).endVertex();
        BufferUploader.drawWithShader(bb.end());
        g.pose().popPose();
    }

    private static boolean ensure() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        int scale = Math.max(2, Math.min(MAX_PIXEL_SCALE, (int) Math.ceil(mc.getWindow().getGuiScale() * 2.0)));
        if (texture != null && pixelScale == scale) return true;
        release();
        pixelScale = scale;
        tilePx = TILE_LOGICAL_SIZE * scale;
        atlasWidth = COLUMN_COUNT * tilePx;
        atlasHeight = 8 * tilePx;
        try {
            BufferedImage image = buildAtlas();
            NativeImage nativeImage = toNativeImage(image);
            texture = new DynamicTexture(nativeImage);
            textureId = new ResourceLocation("chatsphere", "ui/rounded_atlas");
            mc.getTextureManager().register(textureId, texture);
            texture.setFilter(true, false);
            RenderSystem.bindTexture(texture.getId());
            GlStateManager._texParameter(3553, 10241, 9729);
            GlStateManager._texParameter(3553, 10240, 9729);
            return true;
        } catch (RuntimeException e) {
            release();
            return false;
        }
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        if (texture != null && textureId != null && mc != null && mc.getTextureManager() != null) {
            try {
                mc.getTextureManager().release(textureId);
            } catch (RuntimeException ignored) {
            }
        }
        texture = null;
        textureId = null;
    }

    private static BufferedImage buildAtlas() {
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setColor(Color.WHITE);
            for (int radius = 1; radius <= MAX_RADIUS; radius++) {
                int entry = radius - 1;
                int x = entry % COLUMN_COUNT * tilePx;
                int y = entry / COLUMN_COUNT * tilePx;
                int diameter = radius * 2 * pixelScale;
                g2d.fill(new Ellipse2D.Float(x, y, diameter, diameter));
            }
            for (int bw = 1; bw <= MAX_BORDER_WIDTH; bw++) {
                for (int radius = 1; radius <= MAX_RADIUS; radius++) {
                    int entry = MAX_RADIUS + (bw - 1) * MAX_RADIUS + (radius - 1);
                    int x = entry % COLUMN_COUNT * tilePx;
                    int y = entry / COLUMN_COUNT * tilePx;
                    float diameter = radius * 2.0F * pixelScale;
                    Area ring = new Area(new Ellipse2D.Float(x, y, diameter, diameter));
                    int innerRadius = Math.max(0, radius - bw);
                    if (innerRadius > 0) {
                        float inset = bw * pixelScale;
                        ring.subtract(new Area(new Ellipse2D.Float(
                                x + inset,
                                y + inset,
                                innerRadius * 2.0F * pixelScale,
                                innerRadius * 2.0F * pixelScale)));
                    }
                    g2d.fill(ring);
                }
            }
        } finally {
            g2d.dispose();
        }
        return atlas;
    }

    /** NativeImage stores ABGR-packed pixels (A<<24|B<<16|G<<8|R); convert from AWT ARGB. */
    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return nativeImage;
    }
}

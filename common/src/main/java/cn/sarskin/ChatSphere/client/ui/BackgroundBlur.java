package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Panel blur via a GL blit downscale chain (to 1/16) and upscale back —
 * no shaders (Iris-friendly). Physical-pixel based, unaffected by gui scale.
 */
public final class BackgroundBlur {
    private BackgroundBlur() {}

    private static int fbo0 = -1, tex0 = -1; // 1:1 copy
    private static int fbo1 = -1, tex1 = -1; // 1/2
    private static int fbo2 = -1, tex2 = -1; // 1/4
    private static int fbo3 = -1, tex3 = -1; // 1/8
    private static int fbo4 = -1, tex4 = -1; // 1/16
    private static int cw, ch;

    public static void blurScreen(GuiGraphics g, int screenW, int screenH) {
        if (!ModClientConfig.CONFIG.backgroundBlur.get()) return;
        if (screenW <= 0 || screenH <= 0) return;
        try {
            g.flush();
            blurRegion();
        } catch (Exception ignored) {}
    }

    private static void blurRegion() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        int fbW = target.width, fbH = target.height;
        if (fbW <= 0 || fbH <= 0) return;

        int oldFb = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, vp);
        boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);

        try {
            GL30.glDisable(GL30.GL_SCISSOR_TEST);

            ensure(fbW, fbH);
            if (!complete()) {
                destroy();
                return;
            }

            blit(target.frameBufferId, 0, 0, fbW, fbH, fbo0, 0, 0, fbW, fbH);
            blit(fbo0, 0, 0, fbW, fbH, fbo1, 0, 0, Math.max(1, fbW / 2), Math.max(1, fbH / 2));
            blit(fbo1, 0, 0, Math.max(1, fbW / 2), Math.max(1, fbH / 2), fbo2, 0, 0, Math.max(1, fbW / 4), Math.max(1, fbH / 4));
            blit(fbo2, 0, 0, Math.max(1, fbW / 4), Math.max(1, fbH / 4), fbo3, 0, 0, Math.max(1, fbW / 8), Math.max(1, fbH / 8));
            blit(fbo3, 0, 0, Math.max(1, fbW / 8), Math.max(1, fbH / 8), fbo4, 0, 0, Math.max(1, fbW / 16), Math.max(1, fbH / 16));

            blit(fbo4, 0, 0, Math.max(1, fbW / 16), Math.max(1, fbH / 16), fbo3, 0, 0, Math.max(1, fbW / 8), Math.max(1, fbH / 8));
            blit(fbo3, 0, 0, Math.max(1, fbW / 8), Math.max(1, fbH / 8), fbo2, 0, 0, Math.max(1, fbW / 4), Math.max(1, fbH / 4));
            blit(fbo2, 0, 0, Math.max(1, fbW / 4), Math.max(1, fbH / 4), fbo1, 0, 0, Math.max(1, fbW / 2), Math.max(1, fbH / 2));
            blit(fbo1, 0, 0, Math.max(1, fbW / 2), Math.max(1, fbH / 2), fbo0, 0, 0, fbW, fbH);

            blit(fbo0, 0, 0, fbW, fbH, target.frameBufferId, 0, 0, fbW, fbH);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFb);
            GL30.glViewport(vp[0], vp[1], vp[2], vp[3]);
            if (scissor) GL30.glEnable(GL30.GL_SCISSOR_TEST);
        }
    }

    private static void ensure(int pw, int ph) {
        if (pw == cw && ph == ch) return;
        destroy();
        cw = pw;
        ch = ph;
        int[] a = make(pw, ph);
        fbo0 = a[0]; tex0 = a[1];
        int[] b = make(Math.max(1, pw / 2), Math.max(1, ph / 2));
        fbo1 = b[0]; tex1 = b[1];
        int[] c = make(Math.max(1, pw / 4), Math.max(1, ph / 4));
        fbo2 = c[0]; tex2 = c[1];
        int[] d = make(Math.max(1, pw / 8), Math.max(1, ph / 8));
        fbo3 = d[0]; tex3 = d[1];
        int[] e = make(Math.max(1, pw / 16), Math.max(1, ph / 16));
        fbo4 = e[0]; tex4 = e[1];
    }

    private static boolean complete() {
        return status(fbo0) && status(fbo1) && status(fbo2) && status(fbo3) && status(fbo4);
    }

    private static boolean status(int fbo) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    private static int[] make(int w, int h) {
        int fbo = GL30.glGenFramebuffers();
        int tex = GlStateManager._genTexture();
        int oldTex = GL30.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(tex);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0,
                GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL30.GL_TEXTURE_2D, tex, 0);
        GlStateManager._bindTexture(oldTex);
        return new int[]{fbo, tex};
    }

    private static void blit(int srcFbo, int sx0, int sy0, int sx1, int sy1,
                             int dstFbo, int dx0, int dy0, int dw, int dh) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx0 + dw, dy0 + dh,
                GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
    }

    public static void destroy() {
        if (fbo0 != -1) {
            GL30.glDeleteFramebuffers(fbo0); GlStateManager._deleteTexture(tex0);
            GL30.glDeleteFramebuffers(fbo1); GlStateManager._deleteTexture(tex1);
            GL30.glDeleteFramebuffers(fbo2); GlStateManager._deleteTexture(tex2);
            GL30.glDeleteFramebuffers(fbo3); GlStateManager._deleteTexture(tex3);
            GL30.glDeleteFramebuffers(fbo4); GlStateManager._deleteTexture(tex4);
            fbo0 = -1;
            cw = ch = 0;
        }
    }
}

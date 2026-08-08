package cn.sarskin.ChatSphere.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class CopyToast {
    public int ticks;

    public void render(GuiGraphics g, int sidebarWidth, int screenWidth) {
        if (ticks <= 0) return;
        var font = Minecraft.getInstance().font;
        String text = Component.translatable("screen.chatsphere.toast.copied").getString();
        int tw = font.width(text);
        int tx = sidebarWidth + (screenWidth - sidebarWidth - tw) / 2;
        int ty = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
        int alpha = Math.min(255, ticks * 10);
        g.fill(tx - 4, ty - 2, tx + tw + 4, ty + font.lineHeight + 2, (Math.min(alpha, 180) << 24) | 0x000000);
        g.drawString(font, text, tx, ty, (Math.min(alpha, 255) << 24) | 0xFFFFFF, false);
    }

    public void show() { ticks = 30; }
    public void tick() { if (ticks > 0) ticks--; }
}

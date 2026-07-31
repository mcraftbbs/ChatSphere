package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ChatContextMenu {
    private static final int W = 80;
    private static final int ITEM_H = 16;

    public int targetIndex = -1;
    public int contextX;
    public int contextY;
    public String resultText;
    public String resultSender;
    public boolean copyClicked;

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (targetIndex < 0) return;
        var font = Minecraft.getInstance().font;
        int menuH = ITEM_H * 2 + 3;
        int menuX = Math.min(contextX, Minecraft.getInstance().getWindow().getGuiScaledWidth() - W - 10);
        int menuY = contextY - menuH;
        if (menuY < 20) menuY = contextY + 4;

        g.fill(menuX, menuY, menuX + W, menuY + menuH, Theme.panelBg());
        g.renderOutline(menuX, menuY, W, menuH, Theme.popupOutline());

        boolean hoverCopy = mouseY >= menuY && mouseY <= menuY + ITEM_H;
        g.fill(menuX + 1, menuY + 1, menuX + W - 1, menuY + ITEM_H + 1, hoverCopy ? Theme.menuHover() : 0);
        g.drawString(font, Component.translatable("screen.chatsphere.context.copy"), menuX + 8, menuY + 3, 0xCCCCCC, false);

        boolean hoverReply = mouseY >= menuY + ITEM_H + 2 && mouseY <= menuY + ITEM_H * 2 + 2;
        g.fill(menuX + 1, menuY + ITEM_H + 2, menuX + W - 1, menuY + ITEM_H * 2 + 2, hoverReply ? Theme.menuHover() : 0);
        g.drawString(font, Component.translatable("screen.chatsphere.context.reply"), menuX + 8, menuY + ITEM_H + 4, 0xCCCCCC, false);
    }

    public boolean mouseClicked(int mx, int my) {
        if (targetIndex < 0) return false;
        var font = Minecraft.getInstance().font;
        int menuH = ITEM_H * 2 + 3;
        int menuX = Math.min(contextX, Minecraft.getInstance().getWindow().getGuiScaledWidth() - W - 10);
        int menuY = contextY - menuH;
        if (menuY < 20) menuY = contextY + 4;

        if (mx >= menuX && mx <= menuX + W) {
            if (my >= menuY && my <= menuY + ITEM_H) {
                copyClicked = true;
            } else if (my >= menuY + ITEM_H + 2 && my <= menuY + ITEM_H * 2 + 2) {
                resultSender = "pending"; // signal for reply
            }
        }
        targetIndex = -1;
        return true;
    }

    public void clear() { targetIndex = -1; copyClicked = false; resultText = null; resultSender = null; }
}

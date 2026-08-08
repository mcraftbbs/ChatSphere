package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ReplyBarWidget {
    public static final int BAR_HEIGHT = 18;
    private static final int CANCEL_SIZE = 12;
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "textures/gui/close_icon.png");
    public int targetIndex = -1;
    public String replyText;
    public String replySender;
    private Component cachedComponent;
    private String cachedKey;

    public void render(GuiGraphics g, int mouseX, int mouseY, int sidebarWidth, int screenWidth, int rightSidebarWidth, boolean showRight) {
        if (targetIndex < 0 || replyText == null) return;
        var font = Minecraft.getInstance().font;
        int barY = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 14 - 14 - BAR_HEIGHT - 4;
        int barW = screenWidth - sidebarWidth - 20;
        if (showRight) barW -= rightSidebarWidth;

        g.fill(sidebarWidth + 4, barY, sidebarWidth + 4 + barW, barY + BAR_HEIGHT, Theme.replyBarBg());
        Component emojiComponent = componentFor(font, barW - 20 - CANCEL_SIZE);
        int emojiOff = EmojiRegistry.containsPua(emojiComponent) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
        g.drawString(font, emojiComponent, sidebarWidth + 6, barY + (BAR_HEIGHT - font.lineHeight) / 2 + emojiOff, Theme.accent(), false);

        int cancelX = sidebarWidth + 4 + barW - BAR_HEIGHT + (BAR_HEIGHT - CANCEL_SIZE) / 2;
        int cancelY = barY + (BAR_HEIGHT - CANCEL_SIZE) / 2;
        boolean hover = mouseX >= cancelX - 1 && mouseX <= cancelX + CANCEL_SIZE + 1 && mouseY >= cancelY - 1 && mouseY <= cancelY + CANCEL_SIZE + 1;
        if (hover) g.fill(cancelX - 1, cancelY - 1, cancelX + CANCEL_SIZE + 1, cancelY + CANCEL_SIZE + 1, 0x44FF4444);
        g.blit(CLOSE_ICON, cancelX, cancelY, 0, 0, CANCEL_SIZE, CANCEL_SIZE, CANCEL_SIZE, CANCEL_SIZE);
    }

    private Component componentFor(Font font, int maxWidth) {
        String key = replySender + "\u0000" + replyText + "\u0000" + maxWidth;
        if (cachedComponent == null || !key.equals(cachedKey)) {
            cachedKey = key;
            String raw = Component.translatable("screen.chatsphere.reply.prefix", replySender, replyText).getString();
            String truncated = font.plainSubstrByWidth(raw, maxWidth);
            cachedComponent = EmojiRegistry.toComponent(truncated);
        }
        return cachedComponent;
    }

    public boolean isOnCancel(double mouseX, double mouseY, int sidebarWidth, int screenWidth, int rightSidebarWidth, boolean showRight) {
        if (targetIndex < 0) return false;
        int barY = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 14 - 14 - BAR_HEIGHT - 4;
        int barW = screenWidth - sidebarWidth - 20;
        if (showRight) barW -= rightSidebarWidth;
        int cancelX = sidebarWidth + 4 + barW - BAR_HEIGHT;
        return mouseX >= cancelX && mouseX <= cancelX + BAR_HEIGHT - 6 && mouseY >= barY && mouseY <= barY + BAR_HEIGHT - 6;
    }

    public boolean isOnBody(double mouseX, double mouseY, int sidebarWidth, int screenWidth, int rightSidebarWidth, boolean showRight) {
        if (targetIndex < 0) return false;
        int barY = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 14 - 14 - BAR_HEIGHT - 4;
        int barW = screenWidth - sidebarWidth - 20;
        if (showRight) barW -= rightSidebarWidth;
        int cancelX = sidebarWidth + 4 + barW - BAR_HEIGHT;
        return mouseX >= sidebarWidth + 4 && mouseX < cancelX && mouseY >= barY && mouseY <= barY + BAR_HEIGHT;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int sidebarWidth, int screenWidth, int rightSidebarWidth, boolean showRight) {
        if (targetIndex < 0) return false;
        if (isOnCancel(mouseX, mouseY, sidebarWidth, screenWidth, rightSidebarWidth, showRight)) {
            targetIndex = -1;
            replyText = null;
            replySender = null;
            return true;
        }
        return false;
    }

    public void clear() { targetIndex = -1; replyText = null; replySender = null; }
}

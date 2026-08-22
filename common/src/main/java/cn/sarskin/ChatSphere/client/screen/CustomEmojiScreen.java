package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.emoji.CustomEmoji;
import cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.CopyToast;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Manage custom emoji; upload/refresh/delete listed local files (no OS file browser, toast feedback).
 */
public class CustomEmojiScreen extends Screen {
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 340;
    private static final int HEADER_H = 30;
    private static final int LIST_Y = 54;
    private static final int LIST_H = 162;
    private static final int ROW_H = 54;

    private final Screen parent;
    private final CopyToast toast = new CopyToast();
    private final String conversation;
    private String uploadTarget = "";
    private int scrollOffset;
    private int selectedIndex = -1;

    public CustomEmojiScreen(Screen parent, String conversation) {
        super(Component.translatable("screen.chatsphere.custom_emoji.title"));
        this.parent = parent;
        this.conversation = conversation;
    }

    @Override
    protected void init() {
        CustomEmojiRegistry.ensureScanned();
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int maxScroll = Math.max(0, CustomEmojiRegistry.list().size() - visibleRows());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        selectedIndex = Math.min(selectedIndex, Math.max(CustomEmojiRegistry.list().size() - 1, -1));

        int btnX = popupX + 12;
        int btnW = (POPUP_WIDTH - 24 - 8) / 2;
        int row1Y = popupY + POPUP_HEIGHT - 58;
        int row2Y = popupY + POPUP_HEIGHT - 32;

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.custom_emoji.upload_server"),
            btn -> uploadSelectedToServer()
        ).bounds(btnX, row1Y, btnW, 20).style(StyledButton.Style.CONFIRM).build());
        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.custom_emoji.refresh"),
            btn -> {
                CustomEmojiRegistry.scan();
                scrollOffset = 0;
                selectedIndex = -1;
                clearWidgets();
                init();
            }
        ).bounds(btnX + btnW + 8, row1Y, btnW, 20).style(StyledButton.Style.DEFAULT).build());
        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.custom_emoji.done"),
            btn -> onClose()
        ).bounds(btnX, row2Y, POPUP_WIDTH - 24, 20)
            .style(StyledButton.Style.CANCEL).build());
    }

    private int visibleRows() {
        return LIST_H / ROW_H;
    }

    /** Channel-scoped uploads are offered only while chatting in a channel. */
    private boolean channelTargetAvailable() {
        return conversation != null && !conversation.isEmpty()
                && cn.sarskin.ChatSphere.client.ChatHistoryManager.getInstance()
                        .getConversationType(conversation) == cn.sarskin.ChatSphere.client.ChatMessageData.ConversationType.CHANNEL;
    }

    /** Upload-target pill row geometry, shared by render and clicks. */
    private int[] pillLayout() {
        int labelW = font.width(Component.translatable("screen.chatsphere.custom_emoji.target"));
        int x = (width - POPUP_WIDTH) / 2 + 12 + labelW + 8;
        int y = (height - POPUP_HEIGHT) / 2 + 226;
        int pw = font.width(Component.translatable("screen.chatsphere.custom_emoji.target_public")) + 14;
        int cw = channelTargetAvailable()
                ? font.width(Component.translatable("screen.chatsphere.custom_emoji.target_channel")
                        .getString() + " " + conversation) + 14 : 0;
        return new int[]{x, y, pw, cw};
    }

    private int drawTargetPill(GuiGraphics g, int x, int y, Component label, boolean selected, int mouseX, int mouseY) {
        int w = font.width(label) + 14;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        Ui.fillRoundedRect(g, x, y, w, 18, 5, selected ? Theme.activeRow() : (hover ? Theme.hoverRow() : Theme.iconBtnBg()));
        g.drawString(font, label, x + 7, y + 5, selected ? Theme.accent() : Theme.textInactive(), false);
        return w;
    }

    private void uploadSelectedToServer() {
        if (minecraft == null || minecraft.getConnection() == null) {
            toast.show(Component.translatable("screen.chatsphere.custom_emoji.upload_server_offline"));
            return;
        }
        List<CustomEmoji> list = CustomEmojiRegistry.list();
        if (selectedIndex < 0 || selectedIndex >= list.size()) {
            toast.show(Component.translatable("screen.chatsphere.custom_emoji.select_hint"));
            return;
        }
        CustomEmoji emoji = list.get(selectedIndex);
        if (emoji.serverSynced()) {
            toast.show(Component.translatable("screen.chatsphere.custom_emoji.select_hint"));
            return;
        }
        byte[] data = CustomEmojiRegistry.readLocalBytes(emoji.shortcode());
        if (data == null) {
            toast.show(Component.translatable("chatsphere.emoji.err_missing"));
            return;
        }
        CustomEmojiRegistry.uploadToServer(emoji.shortcode(), data, uploadTarget);
        toast.show(Component.translatable("screen.chatsphere.custom_emoji.upload_sent"));
    }

    private void deleteAt(int index) {
        List<CustomEmoji> list = CustomEmojiRegistry.list();
        if (index < 0 || index >= list.size()) return;
        CustomEmoji emoji = list.get(index);
        Component err;
        if (emoji.serverSynced()) {
            CustomEmojiRegistry.deleteFromServer(emoji.shortcode(), emoji.channelId());
            err = null; // the server broadcast will remove it for everyone
        } else {
            err = CustomEmojiRegistry.delete(emoji.shortcode());
        }
        if (err != null) {
            toast.show(err);
            return;
        }
        if (selectedIndex == index) selectedIndex = -1;
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, CustomEmojiRegistry.list().size() - visibleRows()));
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        toast.tick();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int radius = Theme.cardRadius();

        Ui.fillRoundedRect(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupBg());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupOutline());
        }

        int iconX = popupX + 8;
        int iconY = popupY + 6;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, "☺", iconX + (18 - font.width("☺")) / 2, iconY + 4, Theme.text(), false);
        g.drawString(font, title, iconX + 26, popupY + 10, Theme.text(), false);
        int count = CustomEmojiRegistry.list().size();
        g.drawString(font, Component.translatable("screen.chatsphere.custom_emoji.count", count),
                iconX + 26 + font.width(title) + 8, popupY + 10, Theme.textDim(), false);

        int closeX = popupX + POPUP_WIDTH - 8 - 16;
        int closeY = popupY + 7;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);

        String fullPath = CustomEmojiRegistry.dir().toString();
        String hint = Component.translatable("screen.chatsphere.custom_emoji.hint", fullPath).getString();
        String truncatedHint = font.plainSubstrByWidth(hint, POPUP_WIDTH - 24);
        boolean truncated = truncatedHint.length() < hint.length();
        boolean pathHover = mouseX >= popupX + 8 && mouseX < popupX + POPUP_WIDTH - 8
                && mouseY >= popupY + 31 && mouseY < popupY + 46;
        if (pathHover) {
            Ui.fillRoundedRect(g, popupX + 8, popupY + 31, POPUP_WIDTH - 16, 15, 4, Theme.hoverRow());
        }
        g.drawString(font, truncatedHint, popupX + 12, popupY + 34,
            pathHover ? Theme.text() : Theme.textInactive(), false);
        if (truncated) {
            g.drawString(font, "…", popupX + 12 + font.width(truncatedHint), popupY + 34,
                pathHover ? Theme.accent() : Theme.textFaint(), false);
        }

        int[] pills = pillLayout();
        g.drawString(font, Component.translatable("screen.chatsphere.custom_emoji.target"),
                popupX + 12, pills[1] + 5, Theme.textDim(), false);
        drawTargetPill(g, pills[0], pills[1], Component.translatable("screen.chatsphere.custom_emoji.target_public"),
                uploadTarget.isEmpty(), mouseX, mouseY);
        if (channelTargetAvailable()) {
            drawTargetPill(g, pills[0] + pills[2] + 6, pills[1],
                    Component.literal(Component.translatable("screen.chatsphere.custom_emoji.target_channel").getString()
                            + " " + conversation),
                    uploadTarget.equals(conversation), mouseX, mouseY);
        }

        int listX = popupX + 12;
        int listW = POPUP_WIDTH - 24;
        g.fill(listX - 4, popupY + LIST_Y - 2, listX + listW + 4, popupY + LIST_Y + LIST_H + 2, Theme.panelBg2());
        g.enableScissor(listX - 4, popupY + LIST_Y - 2, listX + listW + 4, popupY + LIST_Y + LIST_H + 2);

        List<CustomEmoji> list = CustomEmojiRegistry.list();
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("screen.chatsphere.custom_emoji.empty"),
                    listX + 4, popupY + LIST_Y + 6, Theme.textDim(), false);
        }
        int rows = visibleRows();
        for (int i = scrollOffset; i < Math.min(list.size(), scrollOffset + rows); i++) {
            CustomEmoji emoji = list.get(i);
            int ry = popupY + LIST_Y + (i - scrollOffset) * ROW_H;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H;
            boolean selected = i == selectedIndex && !emoji.serverSynced();
            if (selected) {
                Ui.fillRoundedRect(g, listX, ry + 1, listW, ROW_H - 2, 4, Theme.activeRow());
            } else if (hover) {
                Ui.fillRoundedRect(g, listX, ry + 1, listW, ROW_H - 2, 4, Theme.hoverRow());
            }
            // Fit into the row while keeping the emoji's own aspect ratio.
            int maxH = Math.min(44, ROW_H - 6);
            int ew = emoji.width(), eh = emoji.height();
            double s = Math.min(1.0, Math.min((double) maxH / ew, (double) maxH / eh));
            int pw = Math.max(12, (int) Math.round(ew * s));
            int ph = Math.max(12, (int) Math.round(eh * s));
            emoji.blit(g, listX + 2, ry + (ROW_H - ph) / 2, pw, ph);
            int tx = listX + pw + 6;
            g.drawString(font, emoji.token(), tx, ry + 6, Theme.text(), false);
            tx += font.width(emoji.token()) + 4;
            if (emoji.serverSynced()) {
                if (emoji.channelId() != null && !emoji.channelId().isEmpty()) {
                    g.drawString(font, font.plainSubstrByWidth(emoji.channelId(), 60), tx, ry + 6, 0xFFFFAA66, false);
                } else {
                    String tag = Component.translatable("screen.chatsphere.custom_emoji.server_tag").getString();
                    g.drawString(font, tag, tx, ry + 6, 0xFF66CCFF, false);
                }
            }
            String sizeStr = emoji.width() + "x" + emoji.height();
            g.drawString(font, sizeStr, listX + listW - font.width(sizeStr) - 18, ry + 6, Theme.textDim(), false);
            boolean delHover = mouseX >= listX + listW - 14 && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H;
            g.drawString(font, "×", listX + listW - 10, ry + (ROW_H - 8) / 2,
                delHover ? 0xFFFF6666 : Theme.textFaint(), false);
        }
        g.disableScissor();

        for (var renderable : ((cn.sarskin.ChatSphere.mixin.ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        toast.render(g, 0, width);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        if (my >= popupY + 31 && my < popupY + 46 && mx >= popupX + 8 && mx < popupX + POPUP_WIDTH - 8) {
            if (minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(CustomEmojiRegistry.dir().toString());
                toast.show(Component.translatable("screen.chatsphere.custom_emoji.copy_path"));
            }
            return true;
        }
        int closeX = popupX + POPUP_WIDTH - 8 - 16;
        int closeY = popupY + 7;
        if (mx >= closeX && mx < closeX + 16 && my >= closeY && my < closeY + 16) {
            onClose();
            return true;
        }
        int[] pills = pillLayout();
        if (my >= pills[1] && my < pills[1] + 18) {
            if (mx >= pills[0] && mx < pills[0] + pills[2]) {
                uploadTarget = "";
                return true;
            }
            if (channelTargetAvailable() && mx >= pills[0] + pills[2] + 6
                    && mx < pills[0] + pills[2] + 6 + pills[3]) {
                uploadTarget = conversation;
                return true;
            }
        }
        int listX = popupX + 12;
        int listW = POPUP_WIDTH - 24;
        if (my >= popupY + LIST_Y && my < popupY + LIST_Y + LIST_H && mx >= listX && mx < listX + listW) {
            int rel = (int) ((my - (popupY + LIST_Y)) / ROW_H);
            int index = scrollOffset + rel;
            List<CustomEmoji> list = CustomEmojiRegistry.list();
            if (index >= 0 && index < list.size()) {
                if (mx >= listX + listW - 14) {
                    deleteAt(index);
                    return true;
                }
                selectedIndex = list.get(index).serverSynced() ? -1 : index;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= (width - POPUP_WIDTH) / 2 && mx <= (width + POPUP_WIDTH) / 2
                && my >= (height - POPUP_HEIGHT) / 2 + LIST_Y && my < (height - POPUP_HEIGHT) / 2 + LIST_Y + LIST_H) {
            int max = Math.max(0, CustomEmojiRegistry.list().size() - visibleRows());
            scrollOffset = Mth.clamp(scrollOffset - (int) delta, 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
